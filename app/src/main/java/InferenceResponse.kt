// 경로: kr/co/example/euna/InferenceResponse.kt
package kr.co.example.euna

import com.google.gson.annotations.SerializedName

data class InferenceResponse(
    val predictions: List<Prediction>
)

data class Prediction(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    @SerializedName("class") val className: String,
    val class_id: Int? = null,
    val detection_id: String? = null
)