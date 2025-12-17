package kr.co.example.euna

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream

class FruitDetectionActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private var selectedBitmap: Bitmap? = null

    // UI 요소 선언
    private lateinit var selectButton: Button
    private lateinit var loadingOverlay: View
    private lateinit var guideText: TextView
    private lateinit var guideLayout: LinearLayout
    private lateinit var resultSummaryText: TextView

    // 모델 정보
    private val MODEL_ID = "rotten-fruit-detector-ver-2/3"
    private val API_KEY = "y8pOVn512GTprmKjljSQ"

    // 클래스 맵 (영어 -> 한글 변환용)
    private val CLASS_NAMES_KR = mapOf(
        "bad apple" to "상한 사과",
        "bad banana" to "상한 바나나",
        "bad orange" to "상한 오렌지",
        "bad pomegranate" to "상한 석류",
        "good apple" to "싱싱한 사과",
        "good banana" to "싱싱한 바나나",
        "good orange" to "싱싱한 오렌지",
        "good pomegranate" to "싱싱한 석류"
    )

    // 갤러리/이미지 선택 Launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, it)
                        .copy(Bitmap.Config.ARGB_8888, true)
                }

                imageView.setImageBitmap(selectedBitmap)

                // 이미지를 불러오면 가이드 레이아웃은 숨기고, 이미지뷰를 보이게 설정
                guideLayout.visibility = View.GONE
                imageView.visibility = View.VISIBLE

                resultSummaryText.visibility = View.GONE
                selectedBitmap?.let { runInference(it) }

            } catch (e: Exception) {
                Toast.makeText(this, "이미지 로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ImageLoad", "Error loading bitmap", e)
            }
        }
    }

    // 권한 요청 Launcher
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickImageLauncher.launch("image/*")
        else Toast.makeText(this, "이미지 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_unified)

        // 툴바 설정
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "열매 부패 진단"

        // UI 요소 ID 할당
        imageView = findViewById(R.id.imageView)
        selectButton = findViewById(R.id.selectButton)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        guideText = findViewById(R.id.guideText)
        guideLayout = findViewById(R.id.guideLayout)
        resultSummaryText = findViewById(R.id.resultSummaryText)

        // 카드뷰 클릭 시 갤러리 열기
        val imageContainerCard: View = findViewById(R.id.imageContainerCard)
        imageContainerCard.setOnClickListener {
            checkPermissionAndOpenGallery()
        }
        selectButton.setOnClickListener {
            checkPermissionAndOpenGallery()
        }
    }

    // 메뉴 항목 클릭 이벤트(뒤로가기 버튼 클릭) 처리
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 권한 확인 및 갤러리 실행
    private fun checkPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*")
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    // Roboflow API 호출 및 추론 실행
    private fun runInference(bitmap: Bitmap) {
        selectButton.isEnabled = false
        loadingOverlay.visibility = View.VISIBLE
        resultSummaryText.visibility = View.GONE
        guideLayout.visibility = View.GONE
        imageView.visibility = View.VISIBLE

        val base64Image = encodeBitmapToBase64(bitmap)
        val body = RequestBody.create("text/plain".toMediaTypeOrNull(), base64Image)
        val apiUrl = "https://detect.roboflow.com/$MODEL_ID?api_key=$API_KEY"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.service.inferImage(apiUrl, body).execute()

                withContext(Dispatchers.Main) {
                    selectButton.isEnabled = true
                    loadingOverlay.visibility = View.GONE

                    if (response.isSuccessful) {
                        val result = response.body()
                        if (result != null) {
                            val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                            // 박스 그리기
                            drawBoundingBoxes(annotated, result.predictions)
                            imageView.setImageBitmap(annotated)

                            // 결과 텍스트 업데이트 (상세 분류)
                            updateResultSummary(result.predictions)

                            selectButton.text = "다른 사진 분석하기"
                            Toast.makeText(this@FruitDetectionActivity, "열매 감지 완료 (${result.predictions.size}개)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@FruitDetectionActivity, "응답 파싱 실패", Toast.LENGTH_SHORT).show()
                            selectButton.text = "분석 다시 시도"
                        }
                    } else {
                        val err = response.errorBody()?.string()
                        Log.e("RoboflowAPI", "❌ API Error: ${response.code()} - $err")
                        Toast.makeText(this@FruitDetectionActivity, "API 오류 ${response.code()}", Toast.LENGTH_LONG).show()
                        selectButton.text = "분석 다시 시도"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    selectButton.isEnabled = true
                    loadingOverlay.visibility = View.GONE
                    selectButton.text = "분석 다시 시도"

                    Log.e("RoboflowAPI", "❌ Network Error", e)
                    Toast.makeText(this@FruitDetectionActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ [수정된 부분] 결과 요약 텍스트 업데이트 (종류별 개수 출력)
    private fun updateResultSummary(predictions: List<Prediction>) {
        if (predictions.isEmpty()) {
            resultSummaryText.text = "분석 결과: 감지된 열매가 없습니다."
        } else {
            // 1. 클래스 이름별로 개수를 셉니다.
            val countsMap = predictions.groupingBy { it.className }.eachCount()

            // 2. 한국어 이름으로 변환하고 문자열로 합칩니다. (예: "상한 사과 1개, 싱싱한 바나나 2개")
            val summaryString = countsMap.entries.joinToString(", ") { (className, count) ->
                val krName = CLASS_NAMES_KR[className] ?: className
                "$krName ${count}개"
            }

            resultSummaryText.text = "분석 결과: $summaryString"
        }
        resultSummaryText.visibility = View.VISIBLE
    }

    // 비트맵 Base64 인코딩
    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // dp 단위를 픽셀로 변환
    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    // 바운딩 박스 그리기
    private fun drawBoundingBoxes(bitmap: Bitmap, predictions: List<Prediction>): Bitmap {
        val canvas = Canvas(bitmap)

        val textPaint = Paint().apply {
            textSize = dpToPx(15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        predictions.forEach { p ->
            val isGood = p.className.startsWith("good")

            val boxPaint = Paint().apply {
                color = if (isGood) Color.GREEN else Color.RED
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(4f)
            }

            textPaint.color = boxPaint.color

            val left = p.x - p.width / 2
            val top = p.y - p.height / 2
            val right = p.x + p.width / 2
            val bottom = p.y + p.height / 2

            val classNameKr = CLASS_NAMES_KR[p.className] ?: p.className

            val confidenceFormatted = String.format("%.2f", p.confidence)
            val label = "$classNameKr ($confidenceFormatted)"

            val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            canvas.drawRect(rect, boxPaint)

            canvas.drawText(label, left.toFloat(), top.toFloat() - dpToPx(2f), textPaint)
        }
        return bitmap
    }
}