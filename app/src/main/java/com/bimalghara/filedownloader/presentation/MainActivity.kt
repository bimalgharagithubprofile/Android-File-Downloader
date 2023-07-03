package com.bimalghara.filedownloader.presentation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.broadcast.LocalMessageSender
import com.bimalghara.filedownloader.databinding.ActivityMainBinding
import com.bimalghara.filedownloader.domain.model.DownloadItemState
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.domain.model.ProgressData
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.notification.model.NotificationData
import com.bimalghara.filedownloader.presentation.adapters.DownloadsCardsAdapter
import com.bimalghara.filedownloader.presentation.base.BaseActivity
import com.bimalghara.filedownloader.presentation.base.OnRecyclerViewItemClick
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FileUtil.protectedDirectories
import com.bimalghara.filedownloader.utils.FileUtil.toSize
import com.bimalghara.filedownloader.utils.Logger.logs
import com.bimalghara.filedownloader.utils.permissions.PermissionManager
import com.bimalghara.filedownloader.utils.permissions.Permissions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


/**
 * Created by BimalGhara
 */

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private val logTag = javaClass.simpleName

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    private val permissionManager = PermissionManager.from(this@MainActivity, this@MainActivity)

    private var bottomSheetSettings: BottomSheetBehavior<FrameLayout>?=null
    private var bottomSheetAddNew: BottomSheetBehavior<FrameLayout>?=null

    private lateinit var downloadsCardsAdapter: DownloadsCardsAdapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val progressData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent?.getParcelableExtra("PROGRESS_DATA", ProgressData::class.java)
            else
                @Suppress("DEPRECATION") intent?.getParcelableExtra("PROGRESS_DATA") as? ProgressData?

            if(progressData != null){
                logs(logTag, "receiving ProgressData: ${progressData.progress}")

                downloadsCardsAdapter.updateProgress(progressData)
            } else {
                logs(logTag, "receiving broken Parcelable!")
            }
        }
    }

    private val startActivityForDirectoryPickUp =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val treeUri = result.data?.data
                logs(logTag, "selected treeUri: ${treeUri.toString()}")
                if (treeUri != null) {
                    val treeDocument: DocumentFile? = DocumentFile.fromTreeUri(this, treeUri)
                    logs(logTag, "selected treeDocument: ${treeDocument?.uri?.path}")
                    if(treeDocument != null){
                        logs(logTag, "selected treeDocument canWrite: ${treeDocument.canWrite()}")
                        if(treeDocument.canWrite()){
                            viewModel.setSelectedPath(treeDocument)
                        } else viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_write_permission))
                    } else viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_select_directory_failed))
                } else viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_select_directory_failed))
            }
        }


    private fun startActivityDirectoryPickUp() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForDirectoryPickUp.launch(intent)
    }


    override fun initViewBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        popupMenu()

        val bottomSheetCallback: BottomSheetCallback = object : BottomSheetCallback(){
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when(newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> { }
                    BottomSheetBehavior.STATE_EXPANDED -> { binding.dimLayout.toVisible() }
                    BottomSheetBehavior.STATE_DRAGGING -> { binding.dimLayout.toVisible() }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> { binding.dimLayout.toVisible() }
                    BottomSheetBehavior.STATE_HIDDEN -> { binding.dimLayout.toGone() }
                    BottomSheetBehavior.STATE_SETTLING -> { }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) { }
        }

        bottomSheetSettings = BottomSheetBehavior.from(binding.bottomSheetSettings)
        bottomSheetSettings?.isHideable = true
        bottomSheetSettings?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetSettings?.addBottomSheetCallback(bottomSheetCallback)

        bottomSheetAddNew = BottomSheetBehavior.from(binding.bottomSheetAddNew)
        bottomSheetAddNew?.isHideable = true
        bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetAddNew?.addBottomSheetCallback(bottomSheetCallback)

        binding.addNewSheet.etDestinationFolder.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                permissionManager
                    .request(Permissions.Storage)
                    .rationale("We need all Permissions to save file")
                    .checkPermission { granted ->
                        if (granted) {
                            logs(logTag, "runtime permissions allowed")
                            startActivityDirectoryPickUp()
                        } else {
                            logs(logTag, "runtime permissions denied")
                            viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_no_permission))
                        }
                    }
            } else startActivityDirectoryPickUp()
        }

        binding.btnAddNew.setOnClickListener {
            binding.root.hideKeyboard()
            setAddNew()
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_EXPANDED
        }

        setupDownloadsRecyclerview()
    }

    override fun onStart() {
        super.onStart()
        viewModel.getUsersDataFromCached(this)

        val filter = IntentFilter("${baseContext?.packageName}.NOTIFICATION_BROAD_CAST")
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    override fun onStop() {
        super.onStop()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    private fun setupDownloadsRecyclerview() {
        downloadsCardsAdapter = DownloadsCardsAdapter(this).also {
            it.setOnItemClickListener(object : OnRecyclerViewItemClick<DownloadItemState> {
                override fun onItemClick(data: DownloadItemState) {
                    Log.e(logTag, "Adapter::onItemClick => $data")
                }
            })
        }

        binding.rvDownloadsCards.apply {
            this.layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            this.adapter = downloadsCardsAdapter
        }
    }

    override fun observeViewModel() {
        observeError(binding.root, viewModel.errorSingleEvent)

        observe(viewModel.downloadsLiveData) {
            logs(logTag, "observe downloadsLiveData | $it")
            when (it) {
                is ResourceWrapper.Loading -> {
                    binding.rvDownloadsCards.toGone()
                    binding.noRecordsLayout.toVisible()
                }
                is ResourceWrapper.Success -> {
                    if (!it.data.isNullOrEmpty()) {
                        binding.rvDownloadsCards.toVisible()
                        binding.noRecordsLayout.toGone()

                        downloadsCardsAdapter.updateDataSet(it.data)
                        //submitList(data: MutableMap<String, MutableList<DownloadEntity>>?)
                    } else {
                        binding.noRecordsLayout.toVisible()
                        binding.rvDownloadsCards.toGone()
                    }
                }
                else -> {
                    binding.rvDownloadsCards.toGone()
                    binding.noRecordsLayout.toVisible()
                }
            }
        }

        observe(viewModel.selectedPathLiveData) {
            logs(logTag, "observe selectedPathLiveData | $it")
            if(it?.uri?.path != null) {
                val folder = it.uri.path!!.split("/").last()
                if (protectedDirectories.contains(folder)) {
                    viewModel.setSelectedPath(null)
                    viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_protected_directory))
                    binding.addNewSheet.etDestinationFolder.text = ""
                } else {
                    binding.addNewSheet.etDestinationFolder.text = folder
                }
            }
        }

        observe(viewModel.fileDetailsLiveData) {
            logs(logTag, "observe fileDetailsLiveData | $it")
            when (it) {
                is ResourceWrapper.Loading -> {
                    setGrabbingInfo()
                }
                is ResourceWrapper.Success -> {
                    setEnqueue(it.data)
                }
                else -> {
                    setAddNew()
                }
            }
        }

        observe(viewModel.enqueueLiveData) {
            logs(logTag, "observe enqueueLiveData | $it")
            when (it) {
                is ResourceWrapper.Success -> {
                    setSuccess()
                }
                is ResourceWrapper.Error -> {
                    setFailed(it.error)
                }
                else -> Unit
            }
        }
    }

    private fun popupMenu() {
        val popupMenu = PopupMenu(this, binding.ivMenu)
        popupMenu.inflate(R.menu.main_menu)
        popupMenu.setOnMenuItemClickListener {
            when(it.itemId) {
                R.id.action_pause_all -> {
                    LocalMessageSender.sendMessageToBackground(context = this, action = NotificationAction.DOWNLOAD_PAUSE_ALL.name)
                    true
                }
                R.id.action_resume_all -> {
                    LocalMessageSender.sendMessageToBackground(context = this, action = NotificationAction.DOWNLOAD_RESUME_ALL.name)
                    true
                }
                R.id.action_settings -> {
                    setBottomSheetSettings()
                    bottomSheetSettings?.state = BottomSheetBehavior.STATE_EXPANDED
                    true
                }
                else -> true
            }
        }
        binding.ivMenu.setOnClickListener {
            try {
                val popup = PopupMenu::class.java.getDeclaredField("mPopup")
                popup.isAccessible = true
                val menu = popup.get(popupMenu)
                menu.javaClass
                    .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(menu, true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                popupMenu.show()
            }
        }
    }

    private fun setBottomSheetSettings() = lifecycleScope.launch {
        binding.settingsSheet.seekBar.setCustomThumbDrawable(R.drawable.thumb)
        val getSetting = viewModel.getSettingParallelDownload()
        val downloadLimit = getSetting?.toFloat() ?: DEFAULT_PARALLEL_DOWNLOAD_LIMIT.toFloat()
        binding.settingsSheet.seekBar.value = downloadLimit

        binding.settingsSheet.btnUpdate.setOnClickListener {
            viewModel.updateSettingParallelDownload(binding.settingsSheet.seekBar.value)
            bottomSheetSettings?.state = BottomSheetBehavior.STATE_HIDDEN
        }
        binding.settingsSheet.btnClose.setOnClickListener {
            bottomSheetSettings?.state = BottomSheetBehavior.STATE_HIDDEN
        }

    }

    private fun setAddNew() {
        binding.addNewSheet.groupHeader.toVisible()
        binding.addNewSheet.groupAddNew.toVisible()
        binding.addNewSheet.groupGrabbingInfo.toGone()
        binding.addNewSheet.groupEnqueue.toGone()
        binding.addNewSheet.groupSuccess.toGone()
        binding.addNewSheet.groupFailed.toGone()

        //binding.addNewSheet.etLink.setText("")//eeeeeeeeeee

        binding.addNewSheet.btnClearLink.setOnClickListener {
            binding.addNewSheet.etLink.setText("")
        }
        binding.addNewSheet.btnAddNew.setOnClickListener {
            viewModel.getFileDetails(this, binding.addNewSheet.etLink.text)
        }
        binding.addNewSheet.btnCancelNew.setOnClickListener {
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setGrabbingInfo() {
        binding.addNewSheet.groupHeader.toGone()
        binding.addNewSheet.groupAddNew.toGone()
        binding.addNewSheet.groupGrabbingInfo.toVisible()
        binding.addNewSheet.groupEnqueue.toGone()
        binding.addNewSheet.groupSuccess.toGone()
        binding.addNewSheet.groupFailed.toGone()
    }

    private fun setEnqueue(fileDetails: FileDetails?) {
        binding.addNewSheet.groupHeader.toVisible()
        binding.addNewSheet.groupAddNew.toGone()
        binding.addNewSheet.groupGrabbingInfo.toGone()
        binding.addNewSheet.groupEnqueue.toVisible()
        binding.addNewSheet.groupSuccess.toGone()
        binding.addNewSheet.groupFailed.toGone()


        val size = fileDetails?.contentLength?.toSize(" ")
        if(!size.isNullOrBlank()) {
            binding.addNewSheet.tvFileSize.text = size
        } else binding.addNewSheet.tvFileSize.toInvisible()
        binding.addNewSheet.etFileName.setText(fileDetails?.fileName ?: "")
        if(!fileDetails?.fileExtension.isNullOrBlank())
            binding.addNewSheet.lbFileExt.text =  fileDetails!!.fileExtension
        else
            binding.addNewSheet.lbFileExt.toGone()


        binding.addNewSheet.btnAddEnqueue.setOnClickListener {
            binding.root.hideKeyboard()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionManager
                    .request(Permissions.Notification)
                    .rationale("We need notification Permissions to show progress of download also you may control them like pause the download or cancel them.")
                    .checkPermission { granted ->
                        if (granted) {
                            Log.e(logTag, "notification runtime permissions allowed")
                            addIntoQueue()
                        } else {
                            Log.e(logTag, "Allow notification permissions from Settings")
                            viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_no_permission))
                        }
                    }
            } else {
                addIntoQueue()
            }
        }
        binding.addNewSheet.btnCloseEnqueue.setOnClickListener {
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun addIntoQueue(){
        viewModel.addIntoQueue(
            this,
            binding.addNewSheet.etFileName.text,
            binding.addNewSheet.cbDownloadOverWiFi.isChecked
        )
    }

    private fun setSuccess() {
        binding.addNewSheet.groupHeader.toGone()
        binding.addNewSheet.groupAddNew.toGone()
        binding.addNewSheet.groupGrabbingInfo.toGone()
        binding.addNewSheet.groupEnqueue.toGone()
        binding.addNewSheet.groupSuccess.toVisible()
        binding.addNewSheet.groupFailed.toGone()


        binding.addNewSheet.btnDone.setOnClickListener {
            viewModel.clearSession()
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setFailed(error: String?) {
        binding.addNewSheet.groupHeader.toGone()
        binding.addNewSheet.groupAddNew.toGone()
        binding.addNewSheet.groupGrabbingInfo.toGone()
        binding.addNewSheet.groupEnqueue.toGone()
        binding.addNewSheet.groupSuccess.toGone()
        binding.addNewSheet.groupFailed.toVisible()

        if(!error.isNullOrBlank())
            binding.addNewSheet.tvErrorMessage.text = error

        binding.addNewSheet.btnBackFailed.setOnClickListener {
            setAddNew()
        }
        binding.addNewSheet.btnCloseFailed.setOnClickListener {
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

}