package com.bimalghara.filedownloader.presentation.base

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import com.bimalghara.filedownloader.utils.SingleEvent
import com.bimalghara.filedownloader.utils.showToast
import com.google.android.material.snackbar.Snackbar

/**
 * Created by BimalGhara
 */


abstract class BaseActivity : AppCompatActivity() {

    protected abstract fun initViewBinding()

    abstract fun observeViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initViewBinding()

        observeViewModel()
    }

    fun observeError(view: View, event: LiveData<SingleEvent<Any>>) {
        view.showToast(this, event, Snackbar.LENGTH_LONG)
    }

}
