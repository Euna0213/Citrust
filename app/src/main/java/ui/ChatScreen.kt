package kr.co.example.euna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.co.example.euna.ui.ChatViewModel
import kr.co.example.euna.ui.theme.CitrusOrange
import kr.co.example.euna.ui.theme.FarmGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onNavigateBack: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var userInput by remember { mutableStateOf("") }

    val backgroundCream = Color(0xFFFFF4E1)
    val assistantBubble = Color(0xFFFFFFFF)
    val userBubble = CitrusOrange
    val textDark = Color(0xFF3E2723)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "🌾 농장 도우미 AI 챗봇",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CitrusOrange,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = Color(0xFFFFFAF5)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large),
                        placeholder = { Text("메시지를 입력하세요...") },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = FarmGreen
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                vm.sendMessage(userInput)
                                userInput = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = CitrusOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송"
                        )
                    }
                }
            }
        },
        containerColor = backgroundCream
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundCream)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isUser = msg.role == "user"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isUser) userBubble else assistantBubble,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            shape = if (isUser) {
                                MaterialTheme.shapes.large
                            } else {
                                MaterialTheme.shapes.large
                            },
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.content,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = if (isUser) Color.White else textDark,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = assistantBubble,
                                tonalElevation = 2.dp,
                                shadowElevation = 2.dp,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 10.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "답변 작성 중...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
