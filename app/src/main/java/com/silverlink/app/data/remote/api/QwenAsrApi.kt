package com.silverlink.app.data.remote.api

import com.silverlink.app.data.remote.model.QwenAsrRequest
import com.silverlink.app.data.remote.model.QwenAsrResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Qwen-Audio 语音识别 API (多模态生成接口)
 */
interface QwenAsrApi {
    
    @Headers("Content-Type: application/json")
    @POST("api/v1/services/aigc/multimodal-generation/generation")
    suspend fun transcribe(
        @Body request: QwenAsrRequest
    ): QwenAsrResponse
}
