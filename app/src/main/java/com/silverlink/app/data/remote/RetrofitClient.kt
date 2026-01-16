package com.silverlink.app.data.remote

import com.silverlink.app.data.remote.api.QwenApi
import com.silverlink.app.data.remote.api.QwenAsrApi
import com.silverlink.app.data.remote.api.QwenVisionApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://dashscope.aliyuncs.com/"
    
    // TODO: Replace with your actual API Key
    private const val API_KEY = "YOUR-API-KEY-HERE" 

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Authorization", "Bearer $API_KEY")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: QwenApi by lazy {
        retrofit.create(QwenApi::class.java)
    }

    val visionApi: QwenVisionApi by lazy {
        retrofit.create(QwenVisionApi::class.java)
    }

    val asrApi: QwenAsrApi by lazy {
        retrofit.create(QwenAsrApi::class.java)
    }
}
