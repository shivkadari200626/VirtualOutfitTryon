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

    fun generateOutfit(imageUri: Uri, apiKey: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )

                // Convert Uri to Bitmap for Gemini
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val prompt = "Generate a stylish outfit for this person. Keep the person's face and pose, but change their clothes to a fashionable casual outfit. Return only the new image."
                
                val content = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(content)
                
                // Gemini returns image data in response
                response.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                    if (part is ImagePart) {
                        _resultImage.postValue(part.image)
                        return@launch
                    }
                }
                _error.postValue("No image generated")

            } catch (e: Exception) {
                Log.e("GeminiVM", "Error: ${e.message}", e)
                _error.postValue("Gemini error: ${e.message}")
            }
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
