package com.bimalghara.filedownloader.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ItemDownloadBinding
import com.bimalghara.filedownloader.domain.model.entity.DownloadEntity
import com.bimalghara.filedownloader.presentation.base.OnRecyclerViewItemClick
import com.bimalghara.filedownloader.utils.DownloadStatus
import com.bimalghara.filedownloader.utils.FileType
import com.bimalghara.filedownloader.utils.FileUtil.getFileType
import com.bimalghara.filedownloader.utils.FunUtil.toMegabytes
import com.bimalghara.filedownloader.utils.getStringFromResource

/**
 * Created by BimalGhara
 */

class DownloadsAdapter(
    val context: Context
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var onItemClickListener: OnRecyclerViewItemClick<DownloadEntity>? = null
    fun setOnItemClickListener(onRecyclerViewItemClick: OnRecyclerViewItemClick<DownloadEntity>){
        this.onItemClickListener = onRecyclerViewItemClick
    }

    private val differCallback = object : DiffUtil.ItemCallback<DownloadEntity>(){
        override fun areItemsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
            return oldItem == newItem
        }
    }
    val differ = AsyncListDiffer(this, differCallback)


    inner class UserViewHolder(val binding: ItemDownloadBinding): RecyclerView.ViewHolder(binding.root)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder){
            is UserViewHolder -> {
                val itemDownload = differ.currentList[position]

                when (itemDownload.downloadStatus) {
                    DownloadStatus.WAITING.name -> {
                        holder.binding.ivIcon.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.item_pending
                            )
                        )
                    }
                    DownloadStatus.DOWNLOADING.name -> {
                        holder.binding.ivIcon.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.item_pause
                            )
                        )
                    }
                    DownloadStatus.PAUSED.name -> {
                        holder.binding.ivIcon.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.item_play
                            )
                        )
                        holder.binding.tvAction.text = context.getStringFromResource(R.string.error_paused_download)
                    }
                    DownloadStatus.FAILED.name -> {
                        holder.binding.ivIcon.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.item_failed
                            )
                        )
                        holder.binding.tvAction.text = context.getStringFromResource(R.string.error_failed_to_download)
                    }
                    else -> {
                        when(getFileType(itemDownload.mimeType)){
                            FileType.IMAGE -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_image))
                            FileType.VIDEO -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_video))
                            FileType.AUDIO -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_audio))
                            else -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_doc))
                        }
                        holder.binding.tvAction.text = itemDownload.size.toMegabytes()
                    }
                }

                holder.binding.tvName.text = itemDownload.name

                //click event
                holder.binding.root.setOnClickListener {
                    onItemClickListener?.onItemClick(itemDownload)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}