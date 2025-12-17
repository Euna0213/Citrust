package kr.co.example.euna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kr.co.example.euna.ui.ChatScreen
import kr.co.example.euna.ui.theme.EunaTheme

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EunaTheme {
                // ✅ onNavigateBack 콜백에 finish() 함수를 전달하여 뒤로가기 기능 구현
                ChatScreen(onNavigateBack = { finish() })
            }
        }
    }
}
