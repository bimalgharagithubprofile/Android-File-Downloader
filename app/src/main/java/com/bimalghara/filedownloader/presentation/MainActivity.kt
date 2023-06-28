package com.bimalghara.filedownloader.presentation

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ActivityMainBinding
import com.bimalghara.filedownloader.presentation.base.BaseActivity
import com.bimalghara.filedownloader.utils.toGone
import com.bimalghara.filedownloader.utils.toVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Created by BimalGhara
 */

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private val TAG = javaClass.simpleName

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    var bottomSheetSettings: BottomSheetBehavior<FrameLayout>?=null
    var bottomSheetAddNew: BottomSheetBehavior<FrameLayout>?=null

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


        binding.btnAddNew.setOnClickListener {
            setAddNew()
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_EXPANDED
        }

        handleBottomSheetSettings()
    }

    override fun observeViewModel() {
        observeError(binding.root, viewModel.errorSingleEvent)
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


        binding.addNewSheet.btnClearLink.setOnClickListener {
            binding.addNewSheet.etLink.setText("")
        }
        binding.addNewSheet.btnAddNew.setOnClickListener {
            setGrabbingInfo()
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

        lifecycleScope.launch {
            delay(2500)
            setEnqueue()
        }
    }

    private fun setEnqueue() {
        binding.addNewSheet.groupHeader.toVisible()
        binding.addNewSheet.groupAddNew.toGone()
        binding.addNewSheet.groupGrabbingInfo.toGone()
        binding.addNewSheet.groupEnqueue.toVisible()
        binding.addNewSheet.groupSuccess.toGone()
        binding.addNewSheet.groupFailed.toGone()

        binding.addNewSheet.btnAddEnqueue.setOnClickListener {
            setSuccess()
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
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setFailed() {
        binding.addNewSheet.groupHeader.toGone()
        binding.addNewSheet.groupAddNew.toGone()
        binding.addNewSheet.groupGrabbingInfo.toGone()
        binding.addNewSheet.groupEnqueue.toGone()
        binding.addNewSheet.groupSuccess.toGone()
        binding.addNewSheet.groupFailed.toVisible()


        binding.addNewSheet.btnBackFailed.setOnClickListener {
            setAddNew()
        }
        binding.addNewSheet.btnCloseFailed.setOnClickListener {
            bottomSheetAddNew?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

}