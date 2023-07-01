package com.bimalghara.filedownloader.presentation.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ItemCardBinding
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
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

    private var onItemClickListener: OnRecyclerViewItemClick<DownloadEntity>? = null
    fun setOnItemClickListener(onRecyclerViewItemClick: OnRecyclerViewItemClick<DownloadEntity>){
        this.onItemClickListener = onRecyclerViewItemClick
    }

    private var dataSet = mutableMapOf<String, MutableList<DownloadEntity>>()

    fun updateDataSet(data: MutableMap<String, MutableList<DownloadEntity>>) {
        dataSet.clear()
        dataSet = data
        notifyDataSetChanged()
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
                    it.setOnItemClickListener(object : OnRecyclerViewItemClick<DownloadEntity> {
                        override fun onItemClick(data: DownloadEntity) {
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