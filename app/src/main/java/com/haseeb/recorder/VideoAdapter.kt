package com.haseeb.recorder

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.haseeb.recorder.databinding.ItemVideoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoAdapter(
    private val onPlayClick: (VideoFile) -> Unit,
    private val onRenameClick: (VideoFile) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val videos = mutableListOf<VideoFile>()

    fun submitList(newList: List<VideoFile>) {
        videos.clear()
        videos.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount() = videos.size

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoFile) {
            // Display name without extension
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

            // Load thumbnail using the MediaStore ThumbnailUtils (no Glide needed)
            try {
                val thumb: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    binding.root.context.contentResolver.loadThumbnail(
                        video.uri, Size(200, 200), null
                    )
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(
                        video.uri.path ?: "",
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                }
                if (thumb != null) binding.thumbnail.setImageBitmap(thumb)
                else binding.thumbnail.setImageResource(R.drawable.ic_screen_record)
            } catch (e: Exception) {
                binding.thumbnail.setImageResource(R.drawable.ic_screen_record)
            }

            binding.root.setOnClickListener { onPlayClick(video) }

            binding.btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "▶  Play")
                popup.menu.add(0, 2, 0, "✏  Rename")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { onPlayClick(video); true }
                        2 -> { onRenameClick(video); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}
