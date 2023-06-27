package com.bimalghara.filedownloader.presentation.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Created by BimalGhara
 */


abstract class BaseActivity : AppCompatActivity() {

    protected abstract fun initViewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initViewBinding()
    }

}
