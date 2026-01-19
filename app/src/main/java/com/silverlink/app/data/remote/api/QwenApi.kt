package com.silverlink.app.data.remote.api

import com.silverlink.app.data.remote.model.QwenRequest
import com.silverlink.app.data.remote.model.QwenResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface QwenApi {
    
    @Headers("Content-Type: application/json")
    @POST("api/v1/services/aigc/text-generation/generation")
    suspend fun chat(
        @Body request: QwenRequest
    ): QwenResponse
}
