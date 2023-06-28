package com.bimalghara.filedownloader.presentation

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.lifecycleScope
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ActivityMainBinding
import com.bimalghara.filedownloader.presentation.base.BaseActivity
import com.bimalghara.filedownloader.utils.toGone
import com.bimalghara.filedownloader.utils.toVisible
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

    override fun initViewBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        popupMenu()

        binding.btnAddNew.setOnClickListener {
            showBottomSheetAddNew()
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
                    showBottomSheetSettings()
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

    private fun showBottomSheetSettings() {
        val bottomSheet = Dialog(this)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.bottom_sheet_settings)
        bottomSheet.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bottomSheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        bottomSheet.window?.attributes?.windowAnimations = R.style.BottomSheetAnimation
        bottomSheet.window?.attributes?.gravity = Gravity.BOTTOM


        val btnClose = bottomSheet.findViewById(R.id.btnClose) as AppCompatButton
        val btnUpdate = bottomSheet.findViewById(R.id.btnUpdate) as AppCompatButton

        btnClose.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showBottomSheetAddNew() {
        val bottomSheet = Dialog(this)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.bottom_sheet_add_new)
        bottomSheet.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bottomSheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        bottomSheet.window?.attributes?.windowAnimations = R.style.BottomSheetAnimation
        bottomSheet.window?.attributes?.gravity = Gravity.BOTTOM

        setInitial(bottomSheet)

        bottomSheet.show()
    }

    private fun setInitial(bottomSheet: Dialog) {
        val groupHeader = bottomSheet.findViewById(R.id.groupHeader) as Group
        val groupAdd = bottomSheet.findViewById(R.id.groupAddNew) as Group
        val groupGrabbingInfo = bottomSheet.findViewById(R.id.groupGrabbingInfo) as Group
        val groupEnqueue = bottomSheet.findViewById(R.id.groupEnqueue) as Group
        groupHeader.toVisible()
        groupAdd.toVisible()
        groupGrabbingInfo.toGone()
        groupEnqueue.toGone()


        val etLink = bottomSheet.findViewById(R.id.etLink) as AppCompatEditText
        val btnClearLink = bottomSheet.findViewById(R.id.btnClearLink) as AppCompatImageView
        val btnCancel = bottomSheet.findViewById(R.id.btnCancelNew) as AppCompatButton
        val btnAdd = bottomSheet.findViewById(R.id.btnAddNew) as AppCompatButton

        btnClearLink.setOnClickListener {
            etLink.setText("")
        }
        btnAdd.setOnClickListener {
            setGrabbingInfo(bottomSheet)
        }
        btnCancel.setOnClickListener {
            bottomSheet.dismiss()
        }
    }

    private fun setGrabbingInfo(bottomSheet: Dialog) {
        val groupHeader = bottomSheet.findViewById(R.id.groupHeader) as Group
        val groupAdd = bottomSheet.findViewById(R.id.groupAddNew) as Group
        val groupGrabbingInfo = bottomSheet.findViewById(R.id.groupGrabbingInfo) as Group
        val groupEnqueue = bottomSheet.findViewById(R.id.groupEnqueue) as Group
        groupHeader.toGone()
        groupAdd.toGone()
        groupGrabbingInfo.toVisible()
        groupEnqueue.toGone()

        lifecycleScope.launch {
            delay(2500)
            setEnqueue(bottomSheet)
        }
    }

    private fun setEnqueue(bottomSheet: Dialog) {
        val groupHeader = bottomSheet.findViewById(R.id.groupHeader) as Group
        val groupAdd = bottomSheet.findViewById(R.id.groupAddNew) as Group
        val groupGrabbingInfo = bottomSheet.findViewById(R.id.groupGrabbingInfo) as Group
        val groupEnqueue = bottomSheet.findViewById(R.id.groupEnqueue) as Group
        groupHeader.toVisible()
        groupAdd.toGone()
        groupGrabbingInfo.toGone()
        groupEnqueue.toVisible()

    }

}