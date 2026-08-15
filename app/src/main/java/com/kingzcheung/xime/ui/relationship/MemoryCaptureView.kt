package com.kingzcheung.xime.ui.relationship

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingzcheung.xime.relationship.CurrentPersonStore
import com.kingzcheung.xime.relationship.MemoryCategory
import com.kingzcheung.xime.relationship.MemoryRepository
import com.kingzcheung.xime.relationship.MemoryKind
import com.kingzcheung.xime.relationship.network.MemoryCandidateDto
import com.kingzcheung.xime.relationship.network.RelationshipAiClient
import kotlinx.coroutines.launch

@Composable
fun MemoryCaptureView(
    initialText: String,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    onChoosePerson: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectionFlow = remember(context) { CurrentPersonStore.observe(context) }
    val currentPerson by selectionFlow.collectAsStateWithLifecycle(
        initialValue = CurrentPersonStore.current(context),
    )
    val repository = remember(context) { MemoryRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var content by remember(initialText) {
        mutableStateOf(initialText.trim().take(MemoryRepository.MAX_MEMORY_LENGTH))
    }
    var category by remember { mutableStateOf(MemoryCategory.CURRENT_CONCERN) }
    var kind by remember { mutableStateOf(MemoryKind.FACT) }
    var candidates by remember { mutableStateOf<List<MemoryCandidateDto>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isDegraded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("记一下", color = textColor, fontSize = 16.sp)
                Text(
                    currentPerson?.let { "记录给 ${it.alias}" } ?: "请先选择当前对象",
                    color = if (currentPerson == null) accentColor else textColor.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                )
            }
            Button(
                enabled = currentPerson == null || (content.isNotBlank() && !isAnalyzing),
                onClick = {
                    if (currentPerson == null) {
                        onChoosePerson()
                    } else {
                        val person = currentPerson ?: return@Button
                        isAnalyzing = true
                        scope.launch {
                            val result = RelationshipAiClient.instance.extractMemories(content, person.id)
                            candidates = result.candidates
                            isDegraded = result.degraded
                            isAnalyzing = false
                        }
                    }
                },
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (currentPerson == null) "选择" else "AI 提取")
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = { content = it.take(MemoryRepository.MAX_MEMORY_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("待确认的记忆 · ${content.length}/${MemoryRepository.MAX_MEMORY_LENGTH}") },
            placeholder = { Text("先复制对方消息，或在这里输入一条短记忆") },
            minLines = 1,
            maxLines = 2,
        )

        if (isDegraded) {
            Text("在线提取不可用，已生成本地候选，请务必确认。", color = accentColor, fontSize = 11.sp)
        }
        candidates.take(3).forEach { candidate ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                onClick = {
                    content = candidate.content.take(MemoryRepository.MAX_MEMORY_LENGTH)
                    kind = if (candidate.type == "INFERENCE") MemoryKind.INFERENCE else MemoryKind.FACT
                    category = MemoryCategory.fromId(candidate.category)
                },
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(candidate.content, fontSize = 12.sp)
                    Text(
                        "${if (candidate.type == "INFERENCE") "推测" else "事实"} · 置信度 ${(candidate.confidence * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("分类", color = textColor.copy(alpha = 0.65f), fontSize = 10.sp)
            MemoryCategory.entries.forEach { item ->
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = category == item,
                    onClick = { category = item },
                    label = {
                        Text(
                            when (item) {
                                MemoryCategory.IMPORTANT_EVENT -> "事件"
                                MemoryCategory.CURRENT_CONCERN -> "关注"
                                MemoryCategory.BOUNDARY -> "边界"
                                MemoryCategory.PREFERENCE -> "偏好"
                            },
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("性质", color = textColor.copy(alpha = 0.65f), fontSize = 10.sp)
            MemoryKind.entries.forEach { item ->
                FilterChip(
                    selected = kind == item,
                    onClick = { kind = item },
                    label = { Text(if (item == MemoryKind.FACT) "事实" else "推测", fontSize = 10.sp) },
                )
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = currentPerson != null && content.isNotBlank(),
                onClick = {
                    val person = currentPerson ?: return@Button
                    scope.launch {
                        repository.save(person.id, content, category, kind)
                        onSaved()
                    }
                },
            ) {
                Text("确认保存")
            }
        }
    }
}
