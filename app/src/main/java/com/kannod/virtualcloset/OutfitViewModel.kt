package com.kannod.virtualcloset

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class OutfitViewModel : ViewModel() {

    private val _uiState = MutableStateFlow("")
    val uiState: StateFlow<String> = _uiState

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val GROQ_API_KEY = BuildConfig.GROQ_API_KEY

    fun generateOutfit(context: Context, imageUri: Uri, userPrompt: String) {
        viewModelScope.launch {
            _uiState.value = "Processing image..."
            try {
                val cleanImageBytes = sanitizeImageForApi(context, imageUri, blurFaces = true)
                val base64Image = Base64.encodeToString(cleanImageBytes, Base64.NO_WRAP)
                
                _uiState.value = "Asking Llama 3.1..."
                val result = callGroqApi(base64Image, userPrompt)
                _uiState.value = result
                
            } catch (e: Exception) {
                Log.e("OutfitViewModel", "Error: ${e.message}", e)
                _uiState.value = "Error: ${e.message}"
            }
        }
    }

    private suspend fun sanitizeImageForApi(
        context: Context,
        uri: Uri,
        blurFaces: Boolean = true,
        maxSize: Int = 1024
    ): ByteArray = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        
        // 1. Load bitmap - this strips EXIF automatically
        val originalBitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Can't decode image")

        // 2. Resize if too big
        val scaledBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
            val ratio = minOf(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
            Bitmap.createScaledBitmap(
                originalBitmap,
                (originalBitmap.width * ratio).toInt(),
                (originalBitmap.height * ratio).toInt(),
                true
            )
        } else originalBitmap

        var finalBitmap = scaledBitmap

        // 3. Blur faces on-device with ML Kit
        if (blurFaces) {
            val image = InputImage.fromBitmap(scaledBitmap, 0)
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .enableTracking()
                    .build()
            )

            val faces = detector.process(image).await()  // You were missing .await()
            if (faces.isNotEmpty()) {
                val mutableBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)
                val paint = Paint().apply {
                    isAntiAlias = true
                    maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
                }

                for (face in faces) {
                    val bounds = face.boundingBox
                    val expandedBounds = RectF(bounds).apply {
                        inset(-bounds.width() * 0.15f, -bounds.height() * 0.15f)
                    }
                    canvas
