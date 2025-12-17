package kr.co.example.euna

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 잎 검사
        findViewById<Button>(R.id.btn_leaf_detection).setOnClickListener {
            startActivity(Intent(this, LeafDetectionActivity::class.java))
        }

        // 열매 검사
        findViewById<Button>(R.id.btn_fruit_detection).setOnClickListener {
            startActivity(Intent(this, FruitDetectionActivity::class.java))
        }

        // 벌레 검사
        findViewById<Button>(R.id.btn_pest_detection).setOnClickListener {
            startActivity(Intent(this, PestDetectionActivity::class.java))
        }

        // 🧠 AI 챗봇 버튼 (추가)
        val btnAIChatbot: Button = findViewById(R.id.btn_ai_chatbot)
        btnAIChatbot.isEnabled = true
        btnAIChatbot.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
}
