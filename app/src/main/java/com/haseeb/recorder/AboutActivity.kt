package com.haseeb.recorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.haseeb.recorder.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnSourceCode.setOnClickListener {
            openUrl("https://github.com/muhammadhaseebiqbal-dev/Screen-Recorder")
        }

        binding.btnHaseeb.setOnClickListener {
            openUrl("https://github.com/muhammadhaseebiqbal-dev")
        }

        binding.btnAmeer.setOnClickListener {
            openUrl("https://github.com/ameermuawiya")
        }

        // Load avatars smoothly
        Glide.with(this)
            .load("https://github.com/muhammadhaseebiqbal-dev.png")
            .transform(CircleCrop())
            .into(binding.avatarHaseeb)

        Glide.with(this)
            .load("https://github.com/ameermuawiya.png")
            .transform(CircleCrop())
            .into(binding.avatarAmeer)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
