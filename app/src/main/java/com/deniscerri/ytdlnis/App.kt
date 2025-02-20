package com.deniscerri.ytdlnis

import android.app.Application
import android.app.UiModeManager.MODE_NIGHT_NO
import android.app.UiModeManager.MODE_NIGHT_YES
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.android.material.color.DynamicColors
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executors

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        PreferenceManager.setDefaultValues(
            this,
            "root_preferences",
            MODE_PRIVATE,
            R.xml.root_preferences,
            false
        )

        val sharedPreferences = getSharedPreferences("root_preferences", MODE_PRIVATE)

        applicationScope = CoroutineScope(SupervisorJob())
        applicationScope.launch((Dispatchers.IO)) {
            try {
                initLibraries()
            }catch (e: Exception){
                Toast.makeText(this@App, e.message, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        WorkManager.initialize(
            this@App,
            Configuration.Builder()
                .setExecutor(Executors.newFixedThreadPool(
                    sharedPreferences.getInt("concurrent_downloads", 1)))
                .build())

    }
    @Throws(YoutubeDLException::class)
    private fun initLibraries() {
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
        Aria2c.getInstance().init(this)
    }

    private fun createNotificationChannels() {
        val notificationUtil = NotificationUtil(this)
        notificationUtil.createNotificationChannel()
    }

    companion object {
        private const val TAG = "App"
        private lateinit var applicationScope: CoroutineScope
    }
}