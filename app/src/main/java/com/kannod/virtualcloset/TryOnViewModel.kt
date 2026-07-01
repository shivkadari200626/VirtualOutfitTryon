package com.kannod.virtualcloset

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlobPart
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TryOnViewModel : ViewModel() {

    private val _resultImage = MutableLiveData<Bitmap?>()
    val resultImage: LiveData<Bitmap?> = _resultImage

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun generateOutfit(imageUri: Uri, apiKey: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _error.postValue(null) // Clear previous error
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-pro",
                    apiKey = apiKey
                )

                // Run IO on background thread
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }

                if (bitmap == null) {
                    _error.postValue("Failed to load image")
                    return@launch
                }

                val prompt = "You are a virtual try-on AI. Given this person's photo, replace their current outfit with a stylish casual outfit: blue denim jacket, white t-shirt, black jeans. Keep the person's face, body pose, and background identical. Photorealistic. Return only the final image."

                val content = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(content)

                // Gemini 1.5 Flash returns images as InlineDataPart
                val imagePart = response.candidates
    ?.firstOrNull()?.content?.parts
    ?.filterIsInstance<BlobPart>()  // Changed from InlineDataPart
    ?.firstOrNull()

if (imagePart != null) {
    val imageBytes = imagePart.blob.bytes // Changed from .inlineData
    val resultBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    _resultImage.postValue(resultBitmap)
} else {
    val textResponse = response.text
    Log.d("GeminiVM", "No image in response. Text: $textResponse")
    _error.postValue("Gemini didn't return an image: ${textResponse ?: "Empty response"}")
}

            } catch (e: Exception) {
                Log.e("GeminiVM", "Error: ${e.message}", e)
                _error.postValue("Gemini error: ${e.localizedMessage}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearResult() {
        _resultImage.value = null
    }
} // Class closes here
