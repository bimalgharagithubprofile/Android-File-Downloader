package com.bimalghara.filedownloader.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ItemCardBinding
import com.bimalghara.filedownloader.domain.model.DownloadItemState
import com.bimalghara.filedownloader.domain.model.ProgressData
import com.bimalghara.filedownloader.presentation.base.OnRecyclerViewItemClick
import com.bimalghara.filedownloader.utils.DownloadStatus
import com.bimalghara.filedownloader.utils.Logger.logs
import com.bimalghara.filedownloader.utils.RecyclerViewItemDecoration

/**
 * Created by BimalGhara
 */

class DownloadsCardsAdapter(
    val context: Context
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val logTag = javaClass.simpleName

    private var onItemClickListener: OnRecyclerViewItemClick<DownloadItemState, Boolean>? = null
    fun setOnItemClickListener(onRecyclerViewItemClick: OnRecyclerViewItemClick<DownloadItemState, Boolean>){
        this.onItemClickListener = onRecyclerViewItemClick
    }

    private var dataSet = mapOf<String, MutableList<DownloadItemState>>()

    fun updateDataSet(data: MutableMap<String, MutableList<DownloadItemState>>) {
        dataSet = data
        notifyDataSetChanged()
    }

    fun updateProgress(progressData: ProgressData) {
        val itemState = dataSet.values.flatten().find { item ->
            item.id == progressData.id
        }
        itemState?.let {
            it.tvAction?.text = progressData.actionData?:""

            if(!progressData.isIndeterminate)
                it.tvProgress?.text = "${progressData.progress}%"

            it.progressIndicator?.let{ progressIndicator ->
                logs(logTag, "view isIndeterminate = ${progressIndicator.isIndeterminate}")
                if(progressData.isIndeterminate){
                    if(!progressIndicator.isIndeterminate) {
                        progressIndicator.isIndeterminate = true
                        itemState.progressIndicator = progressIndicator
                    }
                } else {
                    progressIndicator.setProgressCompat(progressData.progress, true)
                }
            }
        }
    }

    inner class CardsViewHolder(val binding: ItemCardBinding): RecyclerView.ViewHolder(binding.root)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder){
            is CardsViewHolder -> {
                val itemDay = dataSet.keys.toTypedArray()[position]

                holder.binding.tvDay.text = itemDay

                val downloadsAdapter = DownloadsAdapter(context).also {
                    it.setOnItemClickListener(object : OnRecyclerViewItemClick<DownloadItemState, Boolean> {
                        override fun onItemClick(data: DownloadItemState, isCanceled: Boolean) {
                            logs(logTag, "DownloadsAdapter::onItemClick => $data")
                            onItemClickListener?.onItemClick(data, isCanceled)
                        }
                    })
                }
                holder.binding.rvDownloadsItems.apply {
                    this.layoutManager =
                        LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                    this.adapter = downloadsAdapter
                    this.addItemDecoration(RecyclerViewItemDecoration(context, R.drawable.divider))
                }
                dataSet[itemDay]?.sortBy { it.downloadStatus == DownloadStatus.COMPLETED.name }
                downloadsAdapter.differ.submitList(dataSet[itemDay])
            }
        }
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }
}