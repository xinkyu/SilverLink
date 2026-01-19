package com.silverlink.app.data.remote.api

import com.silverlink.app.data.remote.model.QwenVisionRequest
import com.silverlink.app.data.remote.model.QwenVisionResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * 通义千问 VL 视觉语言模型 API
 */
interface QwenVisionApi {
    
    @Headers("Content-Type: application/json")
    @POST("api/v1/services/aigc/multimodal-generation/generation")
    suspend fun analyzeImage(
        @Body request: QwenVisionRequest
    ): QwenVisionResponse
}
