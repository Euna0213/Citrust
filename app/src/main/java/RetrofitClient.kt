// 경로: kr/co/example/euna/RetrofitClient.kt
package kr.co.example.euna

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

// ✅ Roboflow는 완전 URL(@Url) 방식으로 호출해야 합니다.
interface RoboflowService {
    @POST
    fun inferImage(
        @Url url: String,      // 예: "https://detect.roboflow.com/cirtus-dgx1b/3?api_key=xxxx"
        @Body body: RequestBody
    ): Call<InferenceResponse>
}

object RetrofitClient {
    private val client = OkHttpClient.Builder().build()

    private const val BASE_URL = "https://detect.roboflow.com/"

    val service: RoboflowService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(RoboflowService::class.java)
    }
}
