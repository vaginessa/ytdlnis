package com.deniscerri.ytdlnis.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    val url: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumb: String,
    val type: DownloadViewModel.Type,
    val time: Long,
    val downloadPath: String,
    val website: String,
    val format: Format
)