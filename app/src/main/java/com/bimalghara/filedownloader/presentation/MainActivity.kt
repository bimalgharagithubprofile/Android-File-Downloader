package com.bimalghara.filedownloader.presentation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.documentfile.provider.DocumentFile
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ActivityMainBinding
import com.bimalghara.filedownloader.domain.model.FileDetails
import com.bimalghara.filedownloader.presentation.base.BaseActivity
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FileUtil.protectedDirectories
import com.bimalghara.filedownloader.utils.FunUtil.toMegabytes
import com.bimalghara.filedownloader.utils.permissions.PermissionManager
import com.bimalghara.filedownloader.utils.permissions.Permissions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import dagger.hilt.android.AndroidEntryPoint


/**
 * Created by BimalGhara
 */

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private val TAG = javaClass.simpleName

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    private val permissionManager = PermissionManager.from(this@MainActivity)

    var bottomSheetSettings: BottomSheetBehavior<FrameLayout>?=null
    var bottomSheetAddNew: BottomSheetBehavior<FrameLayout>?=null

    private val startActivityForDirectoryPickUp =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val treeUri = result.data?.data
                Log.e(TAG, "selected treeUri: ${treeUri.toString()}")
                if (treeUri != null) {
                    val treeDocument: DocumentFile? = DocumentFile.fromTreeUri(this, treeUri)
                    Log.e(TAG, "selected treeDocument: ${treeDocument?.uri?.path}")
                    if(treeDocument != null){
                        Log.e(TAG, "selected treeDocument canWrite: ${treeDocument.canWrite()}")
                        if(treeDocument.canWrite()){
                            viewModel.setSelectedPath(treeDocument)
                        } else viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_write_permission))
                    } else viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_select_directory_failed))
                } else viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_select_directory_failed))
            }
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
                            Log.e(TAG, "runtime permissions allowed")
                            startActivityDirectoryPickUp()
                        } else {
                            Log.e(TAG, "runtime permissions denied")
                            viewModel._errorSingleEvent.value = SingleEvent(getStringFromResource(R.string.error_no_permission))
                        }
                    }
            } else startActivityDirectoryPickUp()
        }

        binding.btnAddNew.setOnClickListener {
            setAddNew()
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_EXPANDED
        }

        handleBottomSheetSettings()
    }

    private fun startActivityDirectoryPickUp() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForDirectoryPickUp.launch(intent)
    }

    override fun observeViewModel() {
        observeError(binding.root, viewModel.errorSingleEvent)

        observe(viewModel.selectedPathLiveData) {
            Log.d(TAG, "observe selectedPathLiveData | $it")
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
            Log.d(TAG, "observe fileDetailsLiveData | $it")
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
            Log.d(TAG, "observe enqueueLiveData | $it")
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
                    true
                }
                R.id.action_resume_all -> {
                    true
                }
                R.id.action_settings -> {
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

    private fun handleBottomSheetSettings() {
        binding.settingsSheet.seekBar.setCustomThumbDrawable(R.drawable.thumb)
        binding.settingsSheet.seekBar.value = (3).toFloat()
        binding.settingsSheet.seekBar.addOnChangeListener { slider, value, fromUser ->
            Log.e(TAG, "seekbar value:${value.toInt()}")
        }

        binding.settingsSheet.btnUpdate.setOnClickListener {
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

        binding.addNewSheet.etLink.setText("")

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


        val size = fileDetails?.contentLength?.toMegabytes()
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
            viewModel.addIntoQueue(
                this,
                binding.addNewSheet.etFileName.text,
                binding.addNewSheet.cbDownloadOverWiFi.isChecked
            )
        }
        binding.addNewSheet.btnCloseEnqueue.setOnClickListener {
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
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