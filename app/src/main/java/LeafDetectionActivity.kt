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

// 질병 상세 정보를 담을 데이터 클래스
data class DiseaseInfo(
    val name: String,
    val symptoms: String,
    val solution: String
)

class LeafDetectionActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private var selectedBitmap: Bitmap? = null

    // UI 요소 선언
    private lateinit var selectButton: Button
    private lateinit var loadingOverlay: View
    private lateinit var guideText: TextView
    private lateinit var guideLayout: LinearLayout
    private lateinit var resultSummaryText: TextView

    // Roboflow API 및 모델 정보
    private val MODEL_ID = "cirtus-dgx1b/3"
    private val API_KEY = "y8pOVn512GTprmKjljSQ"

    // ✅ [수정] 질병별 상세 정보 데이터 (특징 및 대처법)
    private val DISEASE_INFO_MAP = mapOf(
        "blackspot" to DiseaseInfo(
            name = "검은무늬병 (Blackspot)",
            symptoms = "잎과 과실에 흑갈색의 원형 반점이 생기며, 심하면 낙엽이 집니다.",
            solution = "감염된 낙엽과 가지를 제거하여 소각하고, 만코제브 등의 살균제를 주기적으로 살포하세요."
        ),
        "canker" to DiseaseInfo(
            name = "궤양병 (Canker)",
            symptoms = "잎, 가지, 열매에 코르크화된 돌기가 생기며 주변에 노란 띠(halo)가 나타납니다.",
            solution = "바람에 의해 전염되므로 방풍림을 정비하고, 동제(구리) 화합물을 살포하여 예방하세요."
        ),
        "greening" to DiseaseInfo(
            name = "감귤녹화병 (Greening)",
            symptoms = "잎맥 주변이 노랗게 변하거나 얼룩덜룩한 비대칭 무늬가 생기며 열매가 기형이 됩니다.",
            solution = "치료법이 없으므로 감염된 나무는 즉시 제거해야 하며, 매개충인 나무이(Psyllid)를 방제해야 합니다."
        ),
        "melanose" to DiseaseInfo(
            name = "검은점무늬병 (Melanose)",
            symptoms = "잎과 열매에 깨를 뿌린 듯한 작은 검은 점이 박히며, 표면이 거칠어집니다.",
            solution = "죽은 가지에서 균이 서식하므로 전정을 통해 죽은 가지를 철저히 제거하고 살균제를 살포하세요."
        ),
        "healthy" to DiseaseInfo(
            name = "건강한 잎 (Healthy)",
            symptoms = "병해충 피해 없이 깨끗하고 윤기가 흐르는 상태입니다.",
            solution = "현재 상태를 유지하기 위해 주기적인 예찰과 적절한 비배 관리를 지속하세요."
        )
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

                // 아이콘 숨기고, 이미지 뷰 보이기
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

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "잎 질병 진단"

        imageView = findViewById(R.id.imageView)
        selectButton = findViewById(R.id.selectButton)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        guideText = findViewById(R.id.guideText)
        guideLayout = findViewById(R.id.guideLayout)
        resultSummaryText = findViewById(R.id.resultSummaryText)

        val imageContainerCard: View = findViewById(R.id.imageContainerCard)
        imageContainerCard.setOnClickListener {
            checkPermissionAndOpenGallery()
        }
        selectButton.setOnClickListener {
            checkPermissionAndOpenGallery()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
                            drawBoundingBoxes(annotated, result.predictions)
                            imageView.setImageBitmap(annotated)

                            // ✅ [수정] 상세 정보 업데이트 함수 호출
                            updateResultSummary(result.predictions)

                            selectButton.text = "다른 사진 분석하기"
                            Toast.makeText(this@LeafDetectionActivity, "감지 완료 (${result.predictions.size}개)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@LeafDetectionActivity, "응답 파싱 실패", Toast.LENGTH_SHORT).show()
                            selectButton.text = "분석 다시 시도"
                        }
                    } else {
                        val err = response.errorBody()?.string()
                        Log.e("RoboflowAPI", "❌ API Error: ${response.code()} - $err")
                        Toast.makeText(this@LeafDetectionActivity, "API 오류 ${response.code()}", Toast.LENGTH_LONG).show()
                        selectButton.text = "분석 다시 시도"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    selectButton.isEnabled = true
                    loadingOverlay.visibility = View.GONE
                    selectButton.text = "분석 다시 시도"
                    Log.e("RoboflowAPI", "❌ Network Error", e)
                    Toast.makeText(this@LeafDetectionActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ [수정] 감지된 질병들의 상세 정보(특징, 대처법)를 텍스트로 출력하는 함수
    private fun updateResultSummary(predictions: List<Prediction>) {
        if (predictions.isEmpty()) {
            resultSummaryText.text = "분석 결과: 감지된 질병이 없습니다."
        } else {
            val sb = StringBuilder()
            sb.append("🔍 [진단 상세 결과]\n\n")

            // 중복된 질병은 한 번만 보여주기 위해 distinct() 사용
            val distinctClasses = predictions.map { it.className }.distinct()

            distinctClasses.forEach { className ->
                val info = DISEASE_INFO_MAP[className]
                if (info != null) {
                    sb.append("■ ${info.name}\n")
                    sb.append("   - 특징: ${info.symptoms}\n")
                    sb.append("   - 대처: ${info.solution}\n\n")
                } else {
                    // 맵에 정보가 없는 경우 기본 출력
                    sb.append("■ $className\n")
                    sb.append("   - 정보가 등록되지 않았습니다.\n\n")
                }
            }

            resultSummaryText.text = sb.toString().trim()
        }
        resultSummaryText.visibility = View.VISIBLE
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, predictions: List<Prediction>): Bitmap {
        val canvas = Canvas(bitmap)
        val boxPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(4f)
        }

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = dpToPx(15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        predictions.forEach { p ->
            val left = p.x - p.width / 2
            val top = p.y - p.height / 2
            val right = p.x + p.width / 2
            val bottom = p.y + p.height / 2

            // 박스 위에 표시할 때는 간단한 이름(DiseaseInfo의 name 앞부분 등)을 쓰거나 기존 맵을 활용
            // 여기서는 DISEASE_INFO_MAP을 활용해 한국어 이름만 간단히 표시
            val info = DISEASE_INFO_MAP[p.className]
            val labelText = info?.name?.split("(")?.get(0)?.trim() ?: p.className

            val confidenceFormatted = String.format("%.2f", p.confidence)
            val label = "$labelText ($confidenceFormatted)"

            val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            canvas.drawRect(rect, boxPaint)

            canvas.drawText(label, left.toFloat(), top.toFloat() - dpToPx(2f), textPaint)
        }
        return bitmap
    }
}