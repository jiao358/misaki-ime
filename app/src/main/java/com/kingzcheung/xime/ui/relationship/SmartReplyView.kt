package com.kingzcheung.xime.ui.relationship

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingzcheung.xime.relationship.CurrentPersonStore
import com.kingzcheung.xime.relationship.MemoryRepository
import com.kingzcheung.xime.relationship.MemoryRetrieval
import com.kingzcheung.xime.relationship.RelationshipFeedbackManager
import com.kingzcheung.xime.relationship.ReplyPreferenceStore
import com.kingzcheung.xime.relationship.ReplyStyleRanker
import com.kingzcheung.xime.relationship.db.RelationshipDatabase
import com.kingzcheung.xime.relationship.network.RelationshipAiClient
import com.kingzcheung.xime.relationship.network.ReplyCandidateDto
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun SmartReplyView(
    initialMessage: String,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    onChoosePerson: () -> Unit,
    onInsert: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectionFlow = remember(context) { CurrentPersonStore.observe(context) }
    val currentPerson by selectionFlow.collectAsStateWithLifecycle(
        initialValue = CurrentPersonStore.current(context),
    )
    val people by RelationshipDatabase.getInstance(context)
        .relationshipDao()
        .observePeople()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val person = people.firstOrNull { it.id == currentPerson?.id }
    val repository = remember(context) { MemoryRepository.getInstance(context) }
    val memoriesFlow = remember(person?.id) {
        person?.let { repository.observe(it.id) } ?: flowOf(emptyList())
    }
    val memories by memoriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var message by remember(initialMessage) { mutableStateOf(initialMessage.trim().take(2_000)) }
    val relevantMemories = remember(message, memories) {
        MemoryRetrieval.rankRelevant(message, memories)
    }
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<ReplyCandidateDto>>(emptyList()) }
    var requestId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isDegraded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("妙回", color = textColor, fontSize = 16.sp)
                Text(
                    currentPerson?.let { "正在回复 ${it.alias}" } ?: "请先选择当前对象",
                    color = if (currentPerson == null) accentColor else textColor.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                )
            }
            if (currentPerson == null) {
                Button(onClick = onChoosePerson) { Text("选择") }
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(2_000) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("对方刚刚说") },
            placeholder = { Text("先复制对方的一条消息") },
            maxLines = 2,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "仅发送本条消息和 ${relevantMemories.size} 条相关记忆",
                color = textColor.copy(alpha = 0.65f),
                fontSize = 11.sp,
            )
            Button(
                enabled = person != null && message.isNotBlank() && !isLoading,
                onClick = {
                    val target = person ?: return@Button
                    isLoading = true
                    scope.launch {
                        val result = RelationshipAiClient.instance.generateReplies(
                            message = message,
                            anonymousPersonId = target.id,
                            relationshipStage = target.relationshipStage,
                            memories = relevantMemories.map { it.content },
                        )
                        val profile = ReplyPreferenceStore.profile(context, target.id)
                        candidates = ReplyStyleRanker.rank(
                            result.response.candidates,
                            styleOf = { it.style },
                            profile = profile,
                        )
                        requestId = result.response.requestId
                        isDegraded = result.degraded
                        isLoading = false
                    }
                },
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                } else {
                    Text("生成 3 条")
                }
            }
        }

        if (isDegraded) {
            Text(
                "网络服务暂不可用，当前展示本地安全建议。",
                color = accentColor,
                fontSize = 11.sp,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(candidates, key = { it.candidateId }) { candidate ->
                ReplyCandidateCard(
                    candidate = candidate,
                    textColor = textColor,
                    accentColor = accentColor,
                    onInsert = {
                        person?.let { target ->
                            RelationshipFeedbackManager.record(
                                context, target.id, requestId, candidate.candidateId, candidate.style, true,
                            )
                        }
                        onInsert(candidate.text)
                    },
                    onDislike = {
                        person?.let { target ->
                            RelationshipFeedbackManager.record(
                                context, target.id, requestId, candidate.candidateId, candidate.style, false,
                            )
                        }
                        candidates = candidates.filterNot { it.candidateId == candidate.candidateId }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReplyCandidateCard(
    candidate: ReplyCandidateDto,
    textColor: Color,
    accentColor: Color,
    onInsert: () -> Unit,
    onDislike: () -> Unit,
) {
    val styleLabel = when (candidate.style) {
        "humorous" -> "轻松幽默"
        "measured" -> "克制体贴"
        else -> "自然真诚"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(styleLabel, color = accentColor, fontSize = 11.sp)
            Text(candidate.text, color = textColor, fontSize = 14.sp)
            if (candidate.usedMemories.isNotEmpty()) {
                Text(
                    "参考：${candidate.usedMemories.joinToString("；")}",
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDislike) { Text("不合适") }
                TextButton(onClick = onInsert) { Text("插入输入框") }
            }
        }
    }
}
