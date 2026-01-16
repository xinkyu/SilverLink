package com.silverlink.app.data.remote

import com.silverlink.app.data.remote.api.QwenApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://dashscope.aliyuncs.com/"
    
    // TODO: Replace with your actual API Key
    private const val API_KEY = "sk-7d4acccd5a5b48a7b82d0143b5f04ce1" 

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
        .build()

    val api: QwenApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QwenApi::class.java)
    }
}
