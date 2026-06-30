package com.kannod.virtualcloset

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.kannod.virtualcloset.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var personBitmap: Bitmap? = null
    private var garmentBitmap: Bitmap? = null
    
    private val viewModel: TryOnViewModel by viewModels {
        TryOnViewModelFactory(StyleSnapRepository(RetrofitClient.geminiApi))
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            garmentBitmap = BitmapFactory.decodeStream(inputStream)
            binding.stepText.text = "Step 3: Generate your try-on"
            binding.btnGenerate.isEnabled = true
            Toast.makeText(this, "Garment selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnPickGarment.setOnClickListener { pickImage.launch("image/*") }
        binding.btnGenerate.setOnClickListener { generateTryOn() }

        setupObservers()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
            binding.btnGenerate.isEnabled = !loading
            binding.statusText.text = if (loading) "Generating... This takes ~10s" else ""
        }

        viewModel.resultBitmap.observe(this) { bitmap ->
            bitmap?.let {
                val file = File(cacheDir, "tryon_result.jpg")
                file.outputStream().use { out -> it.compress(Bitmap.CompressFormat.JPEG, 100, out) }
                
                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("result_path", file.absolutePath)
                startActivity(intent)
            }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, "Error: $it", Toast.LENGTH_LONG).show()
                binding.statusText.text = "Failed. Try again."
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        val photoFile = File(cacheDir, "person_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    personBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    binding.stepText.text = "Step 2: Pick a garment"
                    binding.btnPickGarment.isEnabled = true
                    binding.btnCapture.isEnabled = false
                    Toast.makeText(baseContext, "Photo captured", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun generateTryOn() {
        val person = personBitmap
        val garment = garmentBitmap
        
        if (person == null || garment == null) {
            Toast.makeText(this, "Missing images", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModel.generate(person, garment, BuildConfig.GEMINI_API_KEY)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
