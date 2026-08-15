package com.kingzcheung.xime.ui.relationship

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
                Text("记一下", color = textColor, fontSize = 16.sp)
                Text(
                    currentPerson?.let { "记录给 ${it.alias}" } ?: "请先选择当前对象",
                    color = if (currentPerson == null) accentColor else textColor.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                )
            }
            if (currentPerson == null) {
                Button(onClick = onChoosePerson) { Text("选择") }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = { content = it.take(MemoryRepository.MAX_MEMORY_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("待确认的记忆") },
            placeholder = { Text("先复制对方消息，或在这里输入一条短记忆") },
            supportingText = { Text("${content.length}/${MemoryRepository.MAX_MEMORY_LENGTH}") },
            minLines = 2,
            maxLines = 3,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                enabled = currentPerson != null && content.isNotBlank() && !isAnalyzing,
                onClick = {
                    val person = currentPerson ?: return@Button
                    isAnalyzing = true
                    scope.launch {
                        val result = RelationshipAiClient.instance.extractMemories(content, person.id)
                        candidates = result.candidates
                        isDegraded = result.degraded
                        isAnalyzing = false
                    }
                },
            ) {
                if (isAnalyzing) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("提取记忆候选")
            }
        }

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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MemoryCategory.entries.forEach { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(item.label) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemoryKind.entries.forEach { item ->
                FilterChip(
                    selected = kind == item,
                    onClick = { kind = item },
                    label = { Text(item.label) },
                )
            }
        }

        Button(
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp),
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
