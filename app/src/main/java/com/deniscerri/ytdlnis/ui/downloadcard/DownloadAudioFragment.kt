package com.deniscerri.ytdlnis.ui.downloadcard

import android.app.Activity
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


class DownloadAudioFragment(private var resultItem: ResultItem, private var currentDownloadItem: DownloadItem?) : Fragment() {
    private var _binding : FragmentHomeBinding? = null
    private var fragmentView: View? = null
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel : ResultViewModel
    private lateinit var fileUtil : FileUtil
    private lateinit var uiUtil : UiUtil

    private lateinit var title : TextInputLayout
    private lateinit var author : TextInputLayout
    private lateinit var saveDir : TextInputLayout
    private lateinit var freeSpace : TextView

    lateinit var downloadItem : DownloadItem
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_download_audio, container, false)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]

        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            downloadItem = withContext(Dispatchers.IO) {
                if (currentDownloadItem != null && currentDownloadItem!!.type == Type.audio){
                    val string = Gson().toJson(currentDownloadItem, DownloadItem::class.java)
                    Gson().fromJson(string, DownloadItem::class.java)
                }else{
                    downloadViewModel.createDownloadItemFromResult(resultItem, Type.audio)
                }
            }
            val sharedPreferences =
                requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)

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
                    audioPathResultLauncher.launch(intent)
                }
                freeSpace = view.findViewById(R.id.freespace)
                freeSpace.text = String.format( getString(R.string.freespace) + ": " + fileUtil.convertFileSize(
                    File(fileUtil.formatPath(downloadItem.downloadPath)).freeSpace
                ))

                var formats = mutableListOf<Format>()
                formats.addAll(resultItem.formats.filter { it.format_note.contains("audio", ignoreCase = true) })
                if (formats.isEmpty()) formats.addAll(downloadItem.allFormats.filter { it.format_note.contains("audio", ignoreCase = true) })

                val containers = requireContext().resources.getStringArray(R.array.audio_containers)
                var containerPreference = sharedPreferences.getString("audio_format", "Default")
                if (containerPreference == "Default") containerPreference = getString(R.string.defaultValue)
                val container = view.findViewById<TextInputLayout>(R.id.downloadContainer)
                val containerAutoCompleteTextView =
                    view.findViewById<AutoCompleteTextView>(R.id.container_textview)

                if (formats.isEmpty()) formats = downloadViewModel.getGenericAudioFormats()

                val formatCard = view.findViewById<ConstraintLayout>(R.id.format_card_constraintLayout)
                val chosenFormat = downloadItem.format
                uiUtil.populateFormatCard(formatCard, chosenFormat)
                val listener = object : OnFormatClickListener {
                    override fun onFormatClick(allFormats: List<List<Format>>, item: List<Format>) {
                        downloadItem.format = item.first()
                        uiUtil.populateFormatCard(formatCard, item.first())
                        if (containers.contains(item.first().container)){
                            downloadItem.format.container = item.first().container
                            containerAutoCompleteTextView.setText(item.first().container, false)
                        }
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO){
                                resultItem.formats.removeAll(formats.toSet())
                                resultItem.formats.addAll(allFormats.first())
                                resultViewModel.update(resultItem)
                            }
                        }
                        formats = allFormats.first().toMutableList()
                    }
                }
                formatCard.setOnClickListener{
                    if (parentFragmentManager.findFragmentByTag("formatSheet") == null){
                        val bottomSheet = FormatSelectionBottomSheetDialog(listOf(downloadItem), listOf(formats), listener)
                        bottomSheet.show(parentFragmentManager, "formatSheet")
                    }
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
                containerAutoCompleteTextView.setText(downloadItem.format.container, false)

                (container!!.editText as AutoCompleteTextView?)!!.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, index: Int, _: Long ->
                        downloadItem.format.container = containers[index]
                    }

                val embedThumb = view.findViewById<Chip>(R.id.embed_thumb)
                embedThumb!!.isChecked = downloadItem.audioPreferences.embedThumb
                embedThumb.setOnClickListener {
                    downloadItem.audioPreferences.embedThumb = embedThumb.isChecked
                }

                val splitByChapters = view.findViewById<Chip>(R.id.split_by_chapters)
                if (downloadItem.downloadSections.isNotBlank()){
                    splitByChapters.isEnabled = false
                    splitByChapters.isChecked = false
                }else{
                    splitByChapters!!.isChecked = downloadItem.audioPreferences.splitByChapters
                }

                splitByChapters.setOnClickListener {
                    downloadItem.audioPreferences.splitByChapters = splitByChapters.isChecked
                }

                val sponsorblock = view.findViewById<Chip>(R.id.sponsorblock_filters)
                sponsorblock!!.setOnClickListener {
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setTitle(getString(R.string.select_sponsorblock_filtering))
                    val values = resources.getStringArray(R.array.sponsorblock_settings_values)
                    val entries = resources.getStringArray(R.array.sponsorblock_settings_entries)
                    val checkedItems : ArrayList<Boolean> = arrayListOf()
                    values.forEach {
                        if (downloadItem.audioPreferences.sponsorBlockFilters.contains(it)) {
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
                        downloadItem.audioPreferences.sponsorBlockFilters.clear()
                        for (i in 0 until checkedItems.size) {
                            if (checkedItems[i]) {
                                downloadItem.audioPreferences.sponsorBlockFilters.add(values[i])
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
                    override fun onChangeCut(list: List<String>) {
                        if (list.isEmpty()){
                            downloadItem.downloadSections = ""
                            cut.text = getString(R.string.cut)

                            splitByChapters.isEnabled = true
                            splitByChapters.isChecked = downloadItem.audioPreferences.splitByChapters
                        }else{
                            var value = ""
                            list.forEach {
                                value += "$it;"
                            }
                            downloadItem.downloadSections = value
                            cut.text = value.dropLast(1)

                            splitByChapters.isEnabled = false
                            splitByChapters.isChecked = false
                        }
                    }
                }
                cut.setOnClickListener {
                    if (parentFragmentManager.findFragmentByTag("cutVideoSheet") == null){
                        val bottomSheet = CutVideoBottomSheetDialog(downloadItem, cutVideoListener)
                        bottomSheet.show(parentFragmentManager, "cutVideoSheet")
                    }
                }

            }catch (e : Exception){
                e.printStackTrace()
            }
        }
    }

    private var audioPathResultLauncher = registerForActivityResult(
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
            //downloadViewModel.updateDownload(downloadItem)
            saveDir.editText?.setText(fileUtil.formatPath(result.data?.data.toString()), TextView.BufferType.EDITABLE)

            freeSpace.text = String.format( getString(R.string.freespace) + ": " + fileUtil.convertFileSize(
                File(fileUtil.formatPath(downloadItem.downloadPath)).freeSpace
            ))
        }
    }

}