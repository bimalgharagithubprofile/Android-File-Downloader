package com.bimalghara.filedownloader.utils

import android.app.Service
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * Created by BimalGhara
 */



fun View.toVisible() {
    this.visibility = View.VISIBLE
}
fun View.toGone() {
    this.visibility = View.GONE
}
fun View.toInvisible() {
    this.visibility = View.GONE
}

fun View.hideKeyboard() {
    (this.context.getSystemService(Service.INPUT_METHOD_SERVICE) as? InputMethodManager)
        ?.hideSoftInputFromWindow(this.windowToken, 0)
}

fun View.showToast(
    lifecycleOwner: LifecycleOwner,
    toastEvent: LiveData<SingleEvent<Any>>,
    timeLength: Int
) {

    toastEvent.observe(lifecycleOwner, Observer { event ->
        event.getContentIfNotHandled()?.let {
            when (it) {
                is String -> Toast.makeText(this.context, it, timeLength).show()
                is Int -> Toast.makeText(this.context, this.context.getString(it), timeLength)
                    .show()
                else -> {
                }
            }
        }
    })
}

fun Context.getStringFromResource(redId: Int): String {
    return getString(redId)
}


