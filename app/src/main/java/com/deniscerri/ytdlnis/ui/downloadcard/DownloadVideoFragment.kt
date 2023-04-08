package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadVideoFragment(private val resultItem: ResultItem, private var currentDownloadItem: DownloadItem?) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var fileUtil : FileUtil
    private lateinit var uiUtil : UiUtil

    private lateinit var title : TextInputLayout
    private lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView

    lateinit var downloadItem: DownloadItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_video, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this@DownloadVideoFragment)[ResultViewModel::class.java]

        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO){
                if (currentDownloadItem != null && currentDownloadItem!!.type == Type.video){
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, Type.video)
                }
            }

            val sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
            try {
                title = view.findViewById(R.id.title_textinput)
                title.editText!!.setText(downloadItem.title)
                title.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.title = p0.toString()
                    }
                })

                author = view.findViewById(R.id.author_textinput)
                author.editText!!.setText(downloadItem.author)
                author.editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(p0: Editable?) {
                        downloadItem.author = p0.toString()
                    }
                })

                saveDir = view.findViewById(R.id.outputPath)
                saveDir.editText!!.setText(
                    fileUtil.formatPath(downloadItem.downloadPath)
                )
                saveDir.editText!!.isFocusable = false
                saveDir.editText!!.isClickable = true
                saveDir.editText!!.setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    videoPathResultLauncher.launch(intent)
                }

                freeSpace = view.findViewById(R.id.freespace)
                freeSpace.text = String.format(getString(R.string.freespace) + ": " + fileUtil.convertFileSize(
                    File(fileUtil.formatPath(downloadItem.downloadPath)).freeSpace
                ))

                var formats = mutableListOf<Format>()
                formats.addAll(resultItem.formats.filter { !it.format_note.contains("audio", ignoreCase = true) })
                val videoFormats = resources.getStringArray(R.array.video_formats)

                val containers = requireContext().resources.getStringArray(R.array.video_containers)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)
                val containerPreference = sharedPreferences.getString("video_format", getString(R.string.defaultValue))

                if (formats.isEmpty()) {
                    videoFormats.forEach { formats.add(Format(it, containerPreference!!,"","", "",0, it)) }
                }

                val formatCard = view.findViewById<ConstraintLayout>(R.id.format_card_constraintLayout)

                val chosenFormat = downloadItem.format
                uiUtil.populateFormatCard(formatCard, chosenFormat)
                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(allFormats: List<Format>, item: Format) {
                        downloadItem.format = item

                        if (containers.contains(item.container)){
                            downloadItem.format.container = item.container
                            containerAutoCompleteTextView.setText(item.container, false)
                        }
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                resultItem.formats.removeAll(formats.toSet())
                                resultItem.formats.addAll(allFormats)
                                resultViewModel.update(resultItem)
                            }
                        }
                        formats = allFormats.toMutableList()
                        uiUtil.populateFormatCard(formatCard, item)
                    }
                }
                formatCard.setOnClickListener{
                    val bottomSheet = FormatSelectionBottomSheetDialog(downloadItem, formats, listener)
                    bottomSheet.show(parentFragmentManager, "formatSheet")
                }

                container?.isEnabled = true
                containerAutoCompleteTextView?.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        containers
                    )
                )
                downloadItem.format.container = containerPreference!!
                containerAutoCompleteTextView!!.setText(downloadItem.format.container, false)

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.format.container = containers[index]
                    }


                val embedSubs = view.findViewById<Chip>(R.id.embed_subtitles)
                embedSubs!!.isChecked = downloadItem.videoPreferences.embedSubs
                embedSubs.setOnClickListener {
                    downloadItem.videoPreferences.embedSubs = embedSubs.isChecked
                }

                val addChapters = view.findViewById<Chip>(R.id.add_chapters)
                addChapters!!.isChecked = downloadItem.videoPreferences.addChapters
                addChapters.setOnClickListener{
                    downloadItem.videoPreferences.addChapters = addChapters.isChecked
                }


                val splitByChapters = view.findViewById<Chip>(R.id.split_by_chapters)
                if(downloadItem.downloadSections.isNotBlank()){
                    splitByChapters.isEnabled = false
                    splitByChapters.isChecked = false
                }else{
                    splitByChapters!!.isChecked = downloadItem.audioPreferences.splitByChapters
                }
                splitByChapters.setOnClickListener {
                    if (splitByChapters.isChecked){
                        addChapters.isEnabled = false
                        addChapters.isChecked = false
                        downloadItem.videoPreferences.addChapters = false
                    }else{
                        addChapters.isEnabled = true
                    }
                    downloadItem.videoPreferences.splitByChapters = splitByChapters.isChecked
                }

                val saveThumbnail = view.findViewById<Chip>(R.id.save_thumbnail)
                saveThumbnail!!.isChecked = downloadItem.SaveThumb
                saveThumbnail.setOnClickListener {
                    downloadItem.SaveThumb = saveThumbnail.isChecked
                }

                val sponsorBlock = view.findViewById<Chip>(R.id.sponsorblock_filters)
                sponsorBlock!!.setOnClickListener {
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle(getString(R.string.select_sponsorblock_filtering))
                    val values = resources.getStringArray(R.array.sponsorblock_settings_values)
                    val entries = resources.getStringArray(R.array.sponsorblock_settings_entries)
                    val checkedItems : ArrayList<Boolean> = arrayListOf()
                    values.forEach {
                        if (downloadItem.videoPreferences.sponsorBlockFilters.contains(it)) {
                            checkedItems.add(true)
                        }else{
                            checkedItems.add(false)
                        }
                    }

                    builder.setMultiChoiceItems(
                        entries,
                        checkedItems.toBooleanArray()
                    ) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }

                    builder.setPositiveButton(
                        getString(R.string.ok)
                    ) { _: DialogInterface?, _: Int ->
                        downloadItem.videoPreferences.sponsorBlockFilters.clear()
                        for (i in 0 until checkedItems.size) {
                            if (checkedItems[i]) {
                                downloadItem.videoPreferences.sponsorBlockFilters.add(values[i])
                            }
                        }
                    }

                    // handle the negative button of the alert dialog
                    builder.setNegativeButton(
                        getString(R.string.cancel)
                    ) { _: DialogInterface?, _: Int -> }


                    val dialog = builder.create()
                    dialog.show()
                }

                val cut = view.findViewById<Chip>(R.id.cut)
                if (downloadItem.downloadSections.isNotBlank()) cut.text = downloadItem.downloadSections
                val cutVideoListener = object : VideoCutListener {

                    override fun onChangeCut(list: Sequence<String>) {
                        if (list.count() == 0){
                            downloadItem.downloadSections = ""
                            cut.text = getString(R.string.cut)

                            splitByChapters.isEnabled = true
                            splitByChapters.isChecked = downloadItem.videoPreferences.splitByChapters
                            if (splitByChapters.isChecked){
                                addChapters.isEnabled = false
                                addChapters.isChecked = false
                            }else{
                                addChapters.isEnabled = true
                            }
                        }else{
                            var value = ""
                            list.forEach {
                                value += "$it;"
                            }
                            downloadItem.downloadSections = value
                            cut.text = value.dropLast(1)

                            splitByChapters.isEnabled = false
                            splitByChapters.isChecked = false
                            addChapters.isEnabled = true
                        }

                    }
                }
                cut.setOnClickListener {
                    val bottomSheet = CutVideoBottomSheetDialog(downloadItem, cutVideoListener)
                    bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                }

                val copyURL = view.findViewById<Chip>(R.id.copy_url)
                copyURL.setOnClickListener {
                    val clipboard: ClipboardManager =
                        requireContext().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setText(downloadItem.url)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var videoPathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            downloadItem.downloadPath = result.data?.data.toString()
            //downloadviewmodel.updateDownload(downloadItem)
            saveDir.editText?.setText(fileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)

            freeSpace.text = String.format(getString(R.string.freespace) + ": " + fileUtil.convertFileSize(
                File(fileUtil.formatPath(downloadItem.downloadPath)).freeSpace
            ))
        }
    }

}