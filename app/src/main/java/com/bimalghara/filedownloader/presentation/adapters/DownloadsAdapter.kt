package com.bimalghara.filedownloader.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bimalghara.filedownloader.R
import com.bimalghara.filedownloader.databinding.ItemDownloadBinding
import com.bimalghara.filedownloader.domain.model.DownloadItemState
import com.bimalghara.filedownloader.presentation.base.OnRecyclerViewItemClick
import com.bimalghara.filedownloader.utils.*
import com.bimalghara.filedownloader.utils.FileUtil.getDomainName
import com.bimalghara.filedownloader.utils.FileUtil.getFileType
import com.bimalghara.filedownloader.utils.FileUtil.toSize

/**
 * Created by BimalGhara
 */

class DownloadsAdapter(
    val context: Context
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var onItemClickListener: OnRecyclerViewItemClick<DownloadItemState>? = null
    fun setOnItemClickListener(onRecyclerViewItemClick: OnRecyclerViewItemClick<DownloadItemState>){
        this.onItemClickListener = onRecyclerViewItemClick
    }

    private val differCallback = object : DiffUtil.ItemCallback<DownloadItemState>(){
        override fun areItemsTheSame(oldItem: DownloadItemState, newItem: DownloadItemState): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadItemState, newItem: DownloadItemState): Boolean {
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
                        holder.binding.tvAction.text = context.getStringFromResource(R.string.error_waiting_in_queue)
                        holder.binding.progressIndicator.toVisible()
                        holder.binding.tvFrom.toGone()
                        holder.binding.tvFromSeparator.toGone()
                    }
                    DownloadStatus.DOWNLOADING.name -> {
                        holder.binding.ivIcon.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.item_pause
                            )
                        )
                        holder.binding.progressIndicator.setIndicatorColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
                        holder.binding.progressIndicator.toVisible()
                        holder.binding.tvFrom.toGone()
                        holder.binding.tvFromSeparator.toGone()
                    }
                    DownloadStatus.PAUSED.name -> {
                        if(itemDownload.interruptedBy == InterruptedBy.NO_WIFI.name) {
                            holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_pending))
                            holder.binding.tvAction.text =
                                context.getStringFromResource(R.string.error_waiting_for_wifi)
                            holder.binding.progressIndicator.setIndicatorColor(ResourcesCompat.getColor(context.resources, R.color.orange, null))
                        } else {
                            holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_play))
                            holder.binding.tvAction.text =
                                context.getStringFromResource(R.string.error_paused_download)
                            holder.binding.progressIndicator.setIndicatorColor(ResourcesCompat.getColor(context.resources, R.color.grey, null))
                        }
                        if(itemDownload.lastProgress > 0)
                            holder.binding.tvProgress.text = "${itemDownload.lastProgress}%"
                        holder.binding.progressIndicator.setProgressCompat(itemDownload.lastProgress, false)
                        holder.binding.progressIndicator.toVisible()
                        holder.binding.tvFrom.toGone()
                        holder.binding.tvFromSeparator.toGone()
                    }
                    DownloadStatus.FAILED.name -> {
                        holder.binding.ivIcon.setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.item_failed
                            )
                        )
                        holder.binding.tvAction.text = context.getStringFromResource(R.string.error_failed_to_download)
                        holder.binding.progressIndicator.setProgressCompat(100, false)
                        holder.binding.progressIndicator.setIndicatorColor(ResourcesCompat.getColor(context.resources, R.color.red, null))
                        holder.binding.progressIndicator.toVisible()
                        holder.binding.tvFrom.toGone()
                        holder.binding.tvFromSeparator.toGone()
                    }
                    else -> {
                        when(getFileType(itemDownload.mimeType)){
                            FileType.IMAGE -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_image))
                            FileType.VIDEO -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_video))
                            FileType.AUDIO -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_audio))
                            else -> holder.binding.ivIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.item_doc))
                        }
                        holder.binding.tvAction.text = itemDownload.size.toSize()
                        holder.binding.progressIndicator.toGone()
                        holder.binding.tvFrom.text = getDomainName(itemDownload.url)
                        holder.binding.tvFrom.toVisible()
                        holder.binding.tvFromSeparator.toVisible()
                    }
                }

                holder.binding.tvName.text = itemDownload.name

                //hold view state
                itemDownload.tvProgress = holder.binding.tvProgress
                itemDownload.progressIndicator = holder.binding.progressIndicator
                itemDownload.tvAction = holder.binding.tvAction

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