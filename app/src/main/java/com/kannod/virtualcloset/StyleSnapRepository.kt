package com.kannod.virtualcloset

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

class StyleSnapRepository(private val api: GeminiApi) {

    suspend fun virtualTryOn(
        personBitmap: Bitmap,
        garmentBitmap: Bitmap,
        apiKey: String
    ): Result<Bitmap> {
        return try {
            val personBase64 = bitmapToBase64(personBitmap)
            val garmentBase64 = bitmapToBase64(garmentBitmap)

            val prompt = """
                Generate a high-quality, photo-realistic image that performs a virtual try-on by combining the following reference inputs.
                This is a single image generation task.
                The goal is to create a seamless and realistic depiction of the person from the first image wearing the specific clothing item shown in the second image.
                Maintain their physical identity, including facial features, hair style, skin tone, and body proportions. The person should remain in the same pose and environment, keeping the background and lighting conditions consistent.
                Modify the subject's attire by replacing their current clothing with the garment provided. The new garment should be rendered with high fidelity, accurately reflecting its color, fabric texture, and any specific patterns or details visible in the reference.
                The clothing must be draped naturally over the person's body, realistically adjusting for their posture, folds in the fabric, and how it interacts with their physique.
                Generate exactly one image.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData("image/jpeg", personBase64)),
                        Part(inlineData = InlineData("image/jpeg", garmentBase64))
                    ))
                )
            )

            val response = api.generateTryOn(apiKey, request)
            
            if (response.isSuccessful) {
                val imageBase64 = response.body()
                    ?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.inlineData != null }
                    ?.inlineData?.data

                if (imageBase64 != null) {
                    Result.success(base64ToBitmap(imageBase64))
                } else {
                    Result.failure(Exception("No image returned from API"))
                }
            } else {
                Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("StyleSnapRepo", "Try-on failed", e)
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64: String): Bitmap {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
