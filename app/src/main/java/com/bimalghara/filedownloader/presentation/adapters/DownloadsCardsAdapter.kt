package com.bimalghara.filedownloader.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ItemCardBinding
import com.bimalghara.filedownloader.domain.model.DownloadItemState
import com.bimalghara.filedownloader.notification.model.NotificationData
import com.bimalghara.filedownloader.presentation.base.OnRecyclerViewItemClick
import com.bimalghara.filedownloader.utils.Logger.logs
import com.bimalghara.filedownloader.utils.RecyclerViewItemDecoration

/**
 * Created by BimalGhara
 */

class DownloadsCardsAdapter(
    val context: Context
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val logTag = javaClass.simpleName

    private var onItemClickListener: OnRecyclerViewItemClick<DownloadItemState>? = null
    fun setOnItemClickListener(onRecyclerViewItemClick: OnRecyclerViewItemClick<DownloadItemState>){
        this.onItemClickListener = onRecyclerViewItemClick
    }

    private var dataSet = mapOf<String, MutableList<DownloadItemState>>()

    fun updateDataSet(data: MutableMap<String, MutableList<DownloadItemState>>) {
        dataSet = data
        notifyDataSetChanged()
    }

    fun updateProgress(notificationData: NotificationData) {
        val itemState = dataSet.values.flatten().find { item ->
            item.id == notificationData.id
        }
        itemState?.let {
            it.tvProgress?.text = "${notificationData.progress}%"
            it.progressIndicator?.setProgressCompat(notificationData.progress, true)
            it.tvAction?.text = notificationData.actionData?:""
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
                    it.setOnItemClickListener(object : OnRecyclerViewItemClick<DownloadItemState> {
                        override fun onItemClick(data: DownloadItemState) {
                            logs(logTag, "DownloadsAdapter::onItemClick => $data")
                            onItemClickListener?.onItemClick(data)
                        }
                    })
                }
                holder.binding.rvDownloadsItems.apply {
                    this.layoutManager =
                        LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                    this.adapter = downloadsAdapter
                    this.addItemDecoration(RecyclerViewItemDecoration(context, R.drawable.divider))
                }
                downloadsAdapter.differ.submitList(dataSet[itemDay])
            }
        }
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }
}