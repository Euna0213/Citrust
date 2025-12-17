package kr.co.example.euna.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ChatViewModel : ViewModel() {

    // ✅ Ktor 클라이언트 설정
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // ✅ 대화 메시지 상태
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // ✅ 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ⚠️ 테스트용 API 키 (배포 시 안전하게 보관!)
    private val apiKey =
        "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"

    init {
        // ✅ 초기 인사 메시지
        _messages.value += ChatMessage(
            role = "assistant",
            content = "안녕하세요 👋 농장 도우미 AI 챗봇입니다.\n궁금한 점이나 농사 관련 조언을 물어보세요!"
        )
    }

    // ✅ 메시지 전송
    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return

        _messages.value += ChatMessage("user", userInput)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // ✅ 요청 JSON 생성
                val requestBody = buildJsonObject {
                    put("model", "gpt-4o-mini") // 또는 gpt-4-turbo 등
                    putJsonArray("messages") {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put(
                                    "content",
                                    "너는 농장 관리 도우미 챗봇이야. 식물 병해, 과일 상태, 해충 방제, 농업 관련 질문에 친절하고 간단하게 조언해줘."
                                )
                            }
                        )
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", userInput)
                            }
                        )
                    }
                }

                // ✅ OpenAI API 호출
                val response: ChatResponse = client.post("https://api.openai.com/v1/chat/completions") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    setBody(requestBody)
                }.body()

                // ✅ 응답 처리
                val reply = response.choices.firstOrNull()?.message?.content
                    ?: "응답을 불러오지 못했어요."
                _messages.value += ChatMessage("assistant", reply)

            } catch (e: Exception) {
                _messages.value += ChatMessage(
                    "assistant",
                    "⚠️ 오류 발생: ${e.localizedMessage}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@Serializable
data class ChatResponse(val choices: List<Choice>)

@Serializable
data class Choice(val message: ChatMessage)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)
