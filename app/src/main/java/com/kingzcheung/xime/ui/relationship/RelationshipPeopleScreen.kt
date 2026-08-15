package com.kingzcheung.xime.ui.relationship

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.relationship.RelationshipViewModel
import com.kingzcheung.xime.relationship.MemoryCategory
import com.kingzcheung.xime.relationship.MemoryRepository
import com.kingzcheung.xime.relationship.MemoryKind
import com.kingzcheung.xime.relationship.RelationshipMemory
import com.kingzcheung.xime.relationship.CommitmentRepository
import com.kingzcheung.xime.relationship.CommitmentStatus
import com.kingzcheung.xime.relationship.combineReminderDateTime
import com.kingzcheung.xime.relationship.reminderDateAsUtcPickerMillis
import com.kingzcheung.xime.relationship.db.CommitmentEntity
import com.kingzcheung.xime.relationship.RelationshipDataExporter
import com.kingzcheung.xime.relationship.db.PersonEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipPeopleScreen(
    onBack: () -> Unit,
    viewModel: RelationshipViewModel = viewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val currentPerson by viewModel.currentPerson.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val screenScope = rememberCoroutineScope()
    var editingPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var memoryPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var editingMemory by remember { mutableStateOf<RelationshipMemory?>(null) }
    var commitmentPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var commitmentSeed by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关系对象") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPerson = null
                    showEditor = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建对象")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前沟通对象", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = currentPerson?.alias ?: "尚未选择",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (currentPerson == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Text(
                            "选择状态保持 30 分钟；输入法会同步显示。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (currentPerson != null) {
                            TextButton(onClick = viewModel::clearSelection) {
                                Text("结束本次会话")
                            }
                        }
                        Row {
                            TextButton(
                                onClick = {
                                    screenScope.launch {
                                        val shareIntent = RelationshipDataExporter.createShareIntent(context)
                                        context.startActivity(Intent.createChooser(shareIntent, "导出关系数据"))
                                    }
                                },
                            ) { Text("导出全部") }
                            TextButton(onClick = { confirmDeleteAll = true }) {
                                Text("删除全部数据")
                            }
                        }
                    }
                }
            }

            if (people.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Text("还没有关系对象", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点击右下角，仅用昵称建立第一个对象。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Text("全部对象", style = MaterialTheme.typography.titleMedium)
                }
                items(people, key = { it.id }) { person ->
                    PersonCard(
                        person = person,
                        selected = currentPerson?.id == person.id,
                        onSelect = { viewModel.select(person) },
                        onEdit = {
                            editingPerson = person
                            showEditor = true
                        },
                        onDelete = { deletingPerson = person },
                        onMemories = { memoryPerson = person },
                        onCommitments = {
                            commitmentPerson = person
                            commitmentSeed = null
                        },
                    )
                }
                item { Spacer(modifier = Modifier.padding(bottom = 72.dp)) }
            }
        }
    }

    if (showEditor) {
        PersonEditorDialog(
            person = editingPerson,
            onDismiss = { showEditor = false },
            onSave = { alias, type, stage, notes ->
                viewModel.save(editingPerson, alias, type, stage, notes)
                showEditor = false
            },
        )
    }

    deletingPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { deletingPerson = null },
            title = { Text("删除 ${person.alias}？") },
            text = { Text("这会删除该对象的本地资料；当前对象也会被清除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(person)
                        deletingPerson = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPerson = null }) { Text("取消") }
            },
        )
    }

    memoryPerson?.let { person ->
        MemoryManagerDialog(
            person = person,
            onDismiss = { memoryPerson = null },
            onEdit = { editingMemory = it },
            onCreateReminder = { memory ->
                memoryPerson = null
                commitmentPerson = person
                commitmentSeed = memory.content
            },
        )
    }

    editingMemory?.let { memory ->
        MemoryEditorDialog(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSaved = { editingMemory = null },
        )
    }


    commitmentPerson?.let { person ->
        CommitmentManagerDialog(
            person = person,
            initialSeed = commitmentSeed,
            onDismiss = {
                commitmentPerson = null
                commitmentSeed = null
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("彻底删除全部关系数据？") },
            text = { Text("人物、记忆、承诺和提醒会从本机删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        confirmDeleteAll = false
                    },
                ) { Text("彻底删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PersonCard(
    person: PersonEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMemories: () -> Unit,
    onCommitments: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Person,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person.alias,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(person.relationshipType, person.relationshipStage)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "未设置关系信息" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑 ${person.alias}")
            }
            IconButton(onClick = onMemories) {
                Icon(Icons.Default.Bookmarks, contentDescription = "管理 ${person.alias} 的记忆")
            }
            IconButton(onClick = onCommitments) {
                Icon(Icons.Default.Notifications, contentDescription = "管理 ${person.alias} 的提醒")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除 ${person.alias}")
            }
        }
    }
}

@Composable
private fun MemoryManagerDialog(
    person: PersonEntity,
    onDismiss: () -> Unit,
    onEdit: (RelationshipMemory) -> Unit,
    onCreateReminder: (RelationshipMemory) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { MemoryRepository.getInstance(context) }
    val memoriesFlow = remember(person.id) { repository.observe(person.id) }
    val memories by memoriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${person.alias} 的记忆") },
        text = {
            if (memories.isEmpty()) {
                Text("还没有已确认的记忆。可在输入法候选栏点击“记一下”。")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(memories, key = { it.id }) { memory ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "${memory.kind.label} · ${memory.category.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(memory.content, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { onEdit(memory) }) { Text("编辑") }
                                TextButton(onClick = { onCreateReminder(memory) }) { Text("提醒") }
                                TextButton(
                                    onClick = { scope.launch { repository.delete(memory.id) } },
                                ) { Text("删除") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun CommitmentManagerDialog(
    person: PersonEntity,
    initialSeed: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { CommitmentRepository.getInstance(context) }
    val commitmentsFlow = remember(person.id) { repository.observe(person.id) }
    val commitments by commitmentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var showEditor by remember(person.id, initialSeed) { mutableStateOf(initialSeed != null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${person.alias} 的承诺与提醒") },
        text = {
            Column {
                Button(onClick = { showEditor = true }) { Text("新建提醒") }
                if (commitments.isEmpty()) {
                    Text(
                        "还没有提醒。也可以从已确认记忆中点击“提醒”创建。",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(commitments, key = { it.id }) { commitment ->
                            CommitmentRow(
                                commitment = commitment,
                                onComplete = { scope.launch { repository.complete(commitment) } },
                                onPostpone = { scope.launch { repository.postponeOneDay(commitment) } },
                                onDelete = { scope.launch { repository.delete(commitment) } },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )

    if (showEditor) {
        CommitmentEditorDialog(
            initialTitle = initialSeed.orEmpty(),
            onDismiss = { showEditor = false },
            onSave = { title, dueAt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                scope.launch { repository.create(person.id, title, dueAt) }
                showEditor = false
            },
        )
    }
}

@Composable
private fun CommitmentRow(
    commitment: CommitmentEntity,
    onComplete: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit,
) {
    val completed = commitment.status == CommitmentStatus.COMPLETED.name
    val formattedDueAt = remember(commitment.dueAt) {
        DateTimeFormatter.ofPattern("MM月dd日 HH:mm")
            .format(Instant.ofEpochMilli(commitment.dueAt).atZone(ZoneId.systemDefault()))
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            commitment.title,
            color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (completed) "已完成" else "提醒时间：$formattedDueAt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!completed) {
                TextButton(onClick = onComplete) { Text("完成") }
                TextButton(onClick = onPostpone) { Text("推迟1天") }
            }
            TextButton(onClick = onDelete) { Text("关闭") }
        }
        HorizontalDivider()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CommitmentEditorDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle.take(160)) }
    var dueAt by remember { mutableLongStateOf(System.currentTimeMillis() + CommitmentRepository.ONE_DAY_MS) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val formattedDueAt = remember(dueAt) {
        DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")
            .format(Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建关系提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(160) },
                    label = { Text("承诺或待办") },
                    minLines = 2,
                    maxLines = 4,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "明天", 3 to "3天后", 7 to "7天后").forEach { (days, label) ->
                        TextButton(
                            onClick = {
                                dueAt = System.currentTimeMillis() + days * CommitmentRepository.ONE_DAY_MS
                            },
                        ) {
                            Text(label)
                        }
                    }
                }
                Text("提醒时间：$formattedDueAt", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showDatePicker = true }) {
                    Text("选择具体日期和时间")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(title, dueAt)
                },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = reminderDateAsUtcPickerMillis(dueAt),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            val currentTime = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
                            dueAt = combineReminderDateTime(
                                selectedUtcDateMillis = selectedDate,
                                hour = currentTime.hour,
                                minute = currentTime.minute,
                            )
                            showDatePicker = false
                            showTimePicker = true
                        }
                    },
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentTime = remember(dueAt) {
            Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
        }
        val timePickerState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                Button(
                    onClick = {
                        dueAt = combineReminderDateTime(
                            selectedUtcDateMillis = reminderDateAsUtcPickerMillis(dueAt),
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                        )
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    memory: RelationshipMemory,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { MemoryRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var content by remember(memory.id) { mutableStateOf(memory.content) }
    var category by remember(memory.id) { mutableStateOf(memory.category) }
    var kind by remember(memory.id) { mutableStateOf(memory.kind) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记忆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(MemoryRepository.MAX_MEMORY_LENGTH) },
                    label = { Text("记忆内容") },
                    minLines = 2,
                    maxLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MemoryCategory.entries.forEach { item ->
                        TextButton(onClick = { category = item }) {
                            Text(
                                item.label,
                                color = if (category == item) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemoryKind.entries.forEach { item ->
                        TextButton(onClick = { kind = item }) {
                            Text(
                                item.label,
                                color = if (kind == item) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = content.isNotBlank(),
                onClick = {
                    scope.launch {
                        repository.save(memory.personId, content, category, kind, memory)
                        onSaved()
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PersonEditorDialog(
    person: PersonEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var alias by remember(person?.id) { mutableStateOf(person?.alias.orEmpty()) }
    var type by remember(person?.id) { mutableStateOf(person?.relationshipType.orEmpty()) }
    var stage by remember(person?.id) { mutableStateOf(person?.relationshipStage.orEmpty()) }
    var notes by remember(person?.id) { mutableStateOf(person?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "新建关系对象" else "编辑关系对象") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("昵称（必填）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("关系类型") },
                    placeholder = { Text("朋友、伴侣、同事…") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = stage,
                    onValueChange = { stage = it },
                    label = { Text("关系阶段") },
                    placeholder = { Text("初识、熟悉、稳定…") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注") },
                    minLines = 2,
                    maxLines = 4,
                )
                HorizontalDivider()
                Text(
                    "请使用昵称，不需要导入通讯录或真实姓名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = alias.isNotBlank(),
                onClick = { onSave(alias, type, stage, notes) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
