package com.kannod.virtualcloset

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kannod.virtualcloset.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val byteArray = intent.getByteArrayExtra("result_image")
        if (byteArray != null) {
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            binding.bgImage.setImageBitmap(bitmap)
        }

        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Save clicked", Toast.LENGTH_SHORT).show()
            // TODO: Add save logic
        }

        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "Share clicked", Toast.LENGTH_SHORT).show()
            // TODO: Add share logic
        }
    }
}
