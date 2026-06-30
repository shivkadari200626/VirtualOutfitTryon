package com.kannod.virtualcloset

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TryOnViewModel : ViewModel() {

    private val _resultImage = MutableLiveData<Bitmap?>()
    val resultImage: LiveData<Bitmap?> = _resultImage

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun generateOutfit(uri: Uri, apiKey: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val userBitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } ?: throw Exception("Failed to load image")

                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash-latest",
                    apiKey = apiKey
                )

                val prompt = """
                    You are a virtual try-on AI. Given this person, generate a new image of them wearing a stylish casual outfit. 
                    Requirements:
                    1. Keep the person's face, pose, and background exactly the same
                    2. Only change the clothing to a trendy outfit: jeans + graphic t-shirt + sneakers
                    3. Make it photorealistic, good lighting, 4k quality
                    4. Output only the final image, no text description
                """.trimIndent()

                val inputContent = content {
                    image(userBitmap)
                    text(prompt)
                }

                val response = withContext(Dispatchers.IO) {
                    model.generateContent(inputContent)
                }

                // Gemini 1.5 Flash returns text + image. Extract the image part.
                val imagePart = response.candidates.firstOrNull()?.content?.parts
                    ?.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>()
                    ?.firstOrNull()

                if (imagePart != null) {
                    val resultBitmap = imagePart.image
                    _resultImage.value = resultBitmap
                } else {
                    throw Exception("No image returned. Response: ${response.text}")
                }

            } catch (e: Exception) {
                _error.value = "Gemini error: ${e.message}"
                _resultImage.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearResult() {
        _resultImage.value = null
    }
}
