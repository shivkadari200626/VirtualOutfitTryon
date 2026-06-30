package com.kannod.virtualcloset

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.kannod.virtualcloset.databinding.ActivityResultBinding
import java.io.File
import java.io.OutputStream

class ResultActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityResultBinding
    private var resultBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra("result_path")
        path?.let {
            resultBitmap = BitmapFactory.decodeFile(it)
            binding.resultImage.setImageBitmap(resultBitmap)
            binding.bgImage.setImageBitmap(resultBitmap)
            
            // Blur background on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.bgImage.setRenderEffect(
                    RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                )
            }
        }

        binding.btnSave.setOnClickListener { saveToGallery() }
        binding.btnShare.setOnClickListener { shareImage() }
    }

    private fun saveToGallery() {
        resultBitmap?.let { bitmap ->
            val filename = "StyleSnap_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StyleSnap")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                }
                Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareImage() {
        resultBitmap?.let { bitmap ->
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "share_image.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }

            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share StyleSnap"))
        }
    }
}
