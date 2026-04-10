package com.haseeb.recorder

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.haseeb.recorder.databinding.ItemVideoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoAdapter(
    private val onPlayClick: (VideoFile) -> Unit,
    private val onRenameClick: (VideoFile) -> Unit,
    private val onDeleteClick: (VideoFile) -> Unit,
    private val onShareClick: (VideoFile) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val videos = mutableListOf<VideoFile>()
    private var lastAnimatedPosition = -1

    fun submitList(newList: List<VideoFile>) {
        val diffCallback = VideoDiffCallback(videos.toList(), newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        videos.clear()
        videos.addAll(newList)
        lastAnimatedPosition = -1
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])

        // Staggered item animation
        if (position > lastAnimatedPosition) {
            val animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.item_slide_up)
            animation.startOffset = (position * 60).toLong()
            holder.itemView.startAnimation(animation)
            lastAnimatedPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.clearAnimation()
    }

    override fun getItemCount() = videos.size

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoFile) {
            binding.titleText.text = video.name.removeSuffix(".mp4")

            val sizeMb = video.size / (1024f * 1024f)
            val sizeStr = String.format(Locale.US, "%.0f MB", sizeMb)

            val totalSecs = video.duration / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            binding.durationText.text = String.format(Locale.US, "%02d:%02d", mins, secs)

            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val dateStr = sdf.format(Date(video.dateAdded * 1000L)).uppercase(Locale.US)

            binding.dateSizeText.text = "$dateStr  •  $sizeStr"

            com.bumptech.glide.Glide.with(binding.root.context)
                .load(video.uri)
                .error(R.drawable.ic_screen_record)
                .into(binding.thumbnail)

            binding.root.setOnClickListener { onPlayClick(video) }

            binding.btnMenu.setOnClickListener { view ->
                val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Rename").setIcon(R.drawable.ic_rename)
                popup.menu.add(0, 2, 0, "Share").setIcon(R.drawable.ic_share_modern)
                popup.menu.add(0, 3, 0, "Delete").setIcon(R.drawable.ic_delete)

                try {
                    val fieldMPopup = androidx.appcompat.widget.PopupMenu::class.java.getDeclaredField("mPopup")
                    fieldMPopup.isAccessible = true
                    val mPopup = fieldMPopup.get(popup)
                    mPopup?.javaClass
                        ?.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                        ?.invoke(mPopup, true)
                } catch (e: Exception) { }

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { onRenameClick(video); true }
                        2 -> { onShareClick(video); true }
                        3 -> { onDeleteClick(video); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    private class VideoDiffCallback(
        private val oldList: List<VideoFile>,
        private val newList: List<VideoFile>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos] == newList[newPos]
    }
}
