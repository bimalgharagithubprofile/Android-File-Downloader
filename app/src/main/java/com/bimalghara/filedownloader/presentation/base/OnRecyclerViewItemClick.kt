package com.bimalghara.filedownloader.presentation.base

interface OnRecyclerViewItemClick<T, Z> {
    fun onItemClick(data: T, isCanceled: Boolean)
}