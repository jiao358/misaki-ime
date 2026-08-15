package com.kingzcheung.xime.ui.relationship

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingzcheung.xime.relationship.CurrentPersonStore
import com.kingzcheung.xime.relationship.db.RelationshipDatabase

@Composable
fun RelationshipPickerView(
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val people by RelationshipDatabase.getInstance(context)
        .relationshipDao()
        .observePeople()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val current by CurrentPersonStore.observe(context)
        .collectAsStateWithLifecycle(initialValue = CurrentPersonStore.current(context))

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
            Text("选择当前对象", color = textColor, fontSize = 16.sp)
        }
        if (people.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = textColor)
                Text("请先在 Misaki 设置中创建关系对象", color = textColor, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(people, key = { it.id }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                CurrentPersonStore.select(context, person.id, person.alias)
                                onBack()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (current?.id == person.id) Icons.Default.CheckCircle else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (current?.id == person.id) accentColor else textColor,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(person.alias, color = textColor, fontSize = 15.sp)
                            Text(
                                listOf(person.relationshipType, person.relationshipStage)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "未设置关系信息" },
                                color = textColor.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
