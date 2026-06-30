package com.kannod.virtualcloset

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/models/gemini-2.5-flash-image-preview:generateContent")
    suspend fun generateTryOn(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

// Request models
data class GeminiRequest(val contents: List<Content>)
data class Content(val parts: List<Part>)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)
data class InlineData(val mimeType: String, val data: String)

// Response models  
data class GeminiResponse(val candidates: List<Candidate>?)
data class Candidate(val content: Content?)
