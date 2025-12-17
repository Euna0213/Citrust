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

// ✅ [추가] 해충 상세 정보를 담을 데이터 클래스
data class PestInfo(
    val name: String,
    val characteristics: String, // 특징
    val impact: String,          // 농작물에 끼치는 영향
    val solution: String         // 대처 방법
)

class PestDetectionActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private var selectedBitmap: Bitmap? = null

    // UI 요소 선언
    private lateinit var selectButton: Button
    private lateinit var loadingOverlay: View
    private lateinit var guideText: TextView
    private lateinit var guideLayout: LinearLayout
    private lateinit var resultSummaryText: TextView

    // 모델 정보
    private val MODEL_ID = "pest-detection-qbalv/3"
    private val API_KEY = "y8pOVn512GTprmKjljSQ"

    // ✅ [추가] 해충별 상세 정보 데이터 (특징, 피해, 대처)
    private val PEST_INFO_MAP = mapOf(
        "Aphid" to PestInfo(
            "진딧물 (Aphid)",
            "작고 연한 몸체를 가졌으며 떼를 지어 서식합니다.",
            "식물의 즙액을 빨아먹어 생육을 저하시키고 그을음병과 바이러스를 매개합니다.",
            "초기에 발견 즉시 잎을 제거하거나, 난황유 및 친환경 약제를 살포하세요. 무당벌레는 천적입니다."
        ),
        "Caterpillar" to PestInfo(
            "애벌레 (Caterpillar)",
            "나비나 나방의 유충으로 씹는 입을 가지고 있습니다.",
            "잎을 갉아먹어 구멍을 내거나 줄기를 훼손하여 광합성을 방해합니다.",
            "보이는 즉시 잡아내거나, 피해가 심할 경우 BT제(미생물 농약) 등을 살포하세요."
        ),
        "Moth" to PestInfo(
            "나방 (Moth)",
            "주로 밤에 활동하며 빛에 모이는 습성이 있습니다.",
            "성충은 알을 낳고, 부화한 유충이 작물을 갉아먹어 큰 피해를 줍니다.",
            "페로몬 트랩이나 포충등을 설치하여 성충을 유인해 포획하세요."
        ),
        "Fruit Flies" to PestInfo(
            "초파리 (Fruit Flies)",
            "크기가 매우 작고 과일 주변을 맴돕니다.",
            "과실에 알을 낳아 애벌레가 과육을 부패시키고 상품성을 떨어뜨립니다.",
            "과수원 주변의 썩은 과일을 즉시 제거하고, 유인 트랩(식초+설탕)을 설치하세요."
        ),
        "Weevil" to PestInfo(
            "바구미 (Weevil)",
            "주둥이가 길게 튀어나온 딱정벌레목 곤충입니다.",
            "쌀, 콩 등 곡물이나 과실 내부에 알을 낳아 유충이 안에서부터 파먹습니다.",
            "저장 곡물은 밀봉하고, 피해 입은 작물은 소각하거나 격리하여 폐기하세요."
        ),
        "Grasshopper" to PestInfo(
            "메뚜기 (Grasshopper)",
            "뒷다리가 발달해 잘 뛰며 씹는 입을 가졌습니다.",
            "잎과 줄기를 닥치는 대로 갉아먹어 생육을 멈추게 합니다.",
            "방충망을 설치하거나, 천적(사마귀, 거미)을 보호하고 친환경 살충제를 사용하세요."
        ),
        "Slug" to PestInfo(
            "민달팽이 (Slug)",
            "껍데기가 없는 달팽이로 습한 곳을 좋아하고 밤에 활동합니다.",
            "채소나 새싹의 잎을 갉아먹고 점액 흔적을 남깁니다.",
            "맥주 트랩을 설치하거나 구리 테이프를 화분 주변에 둘러 접근을 막으세요."
        ),
        "Snail" to PestInfo(
            "달팽이 (Snail)",
            "등에 껍데기가 있으며 습한 환경에서 주로 활동합니다.",
            "민달팽이와 마찬가지로 잎과 어린순을 갉아먹습니다.",
            "토양 표면을 건조하게 관리하고, 유인제나 덫을 놓아 포획하세요."
        ),
        "Spider" to PestInfo(
            "거미 (Spider)",
            "다리가 8개이며 거미줄을 치거나 배회합니다.",
            "농작물에 직접적인 해를 주지 않으며, 오히려 해충을 잡아먹는 익충(유익한 곤충)입니다.",
            "해충 방제에 도움을 주므로 죽이지 말고 보호하는 것이 좋습니다."
        ),
        "Bee" to PestInfo(
            "벌 (Bee)",
            "꽃을 찾아다니며 꿀과 꽃가루를 모읍니다.",
            "꽃가루를 옮겨 열매를 맺게 하는 중요한 화분매개자(익충)입니다.",
            "작물에 유익하므로 살충제 살포 시 주의하여 보호해야 합니다."
        ),
        "Ant" to PestInfo(
            "개미 (Ant)",
            "진딧물과 공생하며 군집 생활을 합니다.",
            "직접적인 피해보다는 진딧물을 보호하여 간접적으로 피해를 확산시킵니다.",
            "개미집 입구에 미끼형 살충제를 놓거나 붕산 트랩을 설치하세요."
        ),
        "Beetle" to PestInfo(
            "딱정벌레 (Beetle)",
            "단단한 등껍질을 가진 곤충입니다.",
            "종류에 따라 잎을 갉아먹거나 뿌리를 해칩니다. (단, 무당벌레 등은 익충)",
            "해충인 경우 눈에 띄는 대로 포획하고, 친환경 유제 등을 사용하세요."
        ),
        "Cockroach" to PestInfo(
            "바퀴벌레 (Cockroach)",
            "비위생적인 환경에서 서식하며 번식력이 강합니다.",
            "농작물보다는 저장고나 시설의 위생을 해치고 병원균을 옮깁니다.",
            "시설 주변의 청결을 유지하고 독미끼를 설치하여 방제하세요."
        ),
        "Earwig" to PestInfo(
            "집게벌레 (Earwig)",
            "꼬리에 집게가 달려있으며 습한 곳을 좋아합니다.",
            "주로 썩은 식물을 먹지만, 때로는 연한 잎이나 과실에 상처를 입힙니다.",
            "젖은 신문지 등을 말아두어 유인한 뒤 포획하여 제거하세요."
        ),
        "Bird" to PestInfo(
            "새 (Bird)",
            "과실이 익을 무렵 날아와 쪼아먹습니다.",
            "잘 익은 과일에 상처를 내어 상품성을 떨어뜨립니다.",
            "방조망을 씌우거나 허수아비, 반사 테이프 등을 이용해 접근을 막으세요."
        ),
        "Wasp" to PestInfo(
            "말벌 (Wasp)",
            "공격성이 강하고 독침이 있습니다.",
            "잘 익은 과일의 당분을 좋아해 과실을 파먹습니다. 사람에게도 위험합니다.",
            "위험하므로 직접 제거하지 말고 전문가에게 의뢰하거나 유인 트랩을 사용하세요."
        ),
        "Scorpion" to PestInfo(
            "전갈 (Scorpion)",
            "독침이 있는 꼬리를 가졌습니다.",
            "작물에 피해를 주기보다는 작업자에게 위험할 수 있습니다. (국내 농경지에서는 보기 드뭅니다)",
            "발견 시 절대 맨손으로 만지지 말고 도구를 이용해 멀리 치우세요."
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

        // 툴바 설정
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "병충해 진단"

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
                            drawBoundingBoxes(annotated, result.predictions)
                            imageView.setImageBitmap(annotated)

                            // ✅ [수정] 상세 정보 업데이트 함수 호출
                            updateResultSummary(result.predictions)

                            selectButton.text = "다른 사진 분석하기"
                            Toast.makeText(this@PestDetectionActivity, "벌레 감지 완료 (${result.predictions.size}개)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@PestDetectionActivity, "응답 파싱 실패", Toast.LENGTH_SHORT).show()
                            selectButton.text = "분석 다시 시도"
                        }
                    } else {
                        val err = response.errorBody()?.string()
                        Log.e("RoboflowAPI", "❌ API Error: ${response.code()} - $err")
                        Toast.makeText(this@PestDetectionActivity, "API 오류 ${response.code()}", Toast.LENGTH_LONG).show()
                        selectButton.text = "분석 다시 시도"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    selectButton.isEnabled = true
                    loadingOverlay.visibility = View.GONE
                    selectButton.text = "분석 다시 시도"

                    Log.e("RoboflowAPI", "❌ Network Error", e)
                    Toast.makeText(this@PestDetectionActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ [수정] 감지된 해충의 상세 정보(특징, 피해, 대처)를 텍스트로 출력하는 함수
    private fun updateResultSummary(predictions: List<Prediction>) {
        if (predictions.isEmpty()) {
            resultSummaryText.text = "분석 결과: 감지된 해충이 없습니다."
        } else {
            val sb = StringBuilder()
            sb.append("🔍 [해충 진단 상세 결과]\n\n")

            // 중복된 해충은 한 번만 보여주기 위해 distinct() 사용
            val distinctClasses = predictions.map { it.className }.distinct()

            distinctClasses.forEach { className ->
                val info = PEST_INFO_MAP[className]
                if (info != null) {
                    sb.append("■ ${info.name}\n")
                    sb.append("   - 특징: ${info.characteristics}\n")
                    sb.append("   - 피해: ${info.impact}\n")
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

            // 박스 위 라벨 표시 (맵 정보 활용)
            val info = PEST_INFO_MAP[p.className]
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