package com.example.countdowndays.ui.editevent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.countdowndays.data.RepeatType
import com.example.countdowndays.ui.common.PillButton
import com.example.countdowndays.ui.theme.EventPalette
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.LunarCalendar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventScreen(
    eventId: Long?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    vm: AddEditEventViewModel = com.example.countdowndays.ui.common.appViewModel {
        AddEditEventViewModel(it.repository)
    }
) {
    LaunchedEffect(eventId) { vm.load(eventId) }

    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImageStorage.saveFromUri(context, uri, "event")?.let { vm.setImage(it) }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    val isEdit = eventId != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑事件" else "新建事件") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.save(onSaved) },
                        enabled = state.loaded && state.name.isNotBlank()
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "保存",
                            tint = if (state.name.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ---------- 配图 ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .clickable {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val path = state.imagePath
                if (!path.isNullOrEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(path)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { vm.clearImage() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除图片",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "添加配图（可选）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- 名称 ----------
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                label = { Text("事件名称") },
                placeholder = { Text("例如：和闺蜜认识、毕业、旅行出发") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // ---------- 日期 ----------
            SectionLabel("日期")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.isLunar) {
                            LunarCalendar.format(state.lunarMonth, state.lunarDay)
                        } else {
                            DateUtils.formatDateWithWeek(state.dateMillis)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "公历 ${DateUtils.formatDate(state.dateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.isLunar,
                    onCheckedChange = vm::setLunar
                )
            }
            Text(
                text = "打开右侧开关按农历记录（生日、祭日等）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (state.isLunar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.lunarMonth.toString(),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.toIntOrNull()?.let { m ->
                                if (m in 1..12) vm.setLunarDate(m, state.lunarDay)
                            }
                        },
                        label = { Text("农历月") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = state.lunarDay.toString(),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.toIntOrNull()?.let { d ->
                                if (d in 1..30) vm.setLunarDate(state.lunarMonth, d)
                            }
                        },
                        label = { Text("农历日") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // ---------- 重复 ----------
            SectionLabel("重复")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RepeatType.entries.size) { index ->
                    val type = RepeatType.entries[index]
                    PillButton(
                        text = when (type) {
                            RepeatType.NONE -> "不重复"
                            RepeatType.DAILY -> "每天"
                            RepeatType.WEEKLY -> "每周"
                            RepeatType.MONTHLY -> "每月"
                            RepeatType.YEARLY -> "每年"
                        },
                        selected = state.repeatType == type,
                        onClick = { vm.setRepeat(type) }
                    )
                }
            }

            // ---------- 分类 ----------
            SectionLabel("分类")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PillButton(
                        text = "未分类",
                        selected = state.categoryId == null,
                        onClick = { vm.setCategory(null) }
                    )
                }
                items(categories.size) { index ->
                    val cat = categories[index]
                    PillButton(
                        text = cat.name,
                        selected = state.categoryId == cat.id,
                        color = if (state.categoryId == cat.id) eventColor(cat.colorIndex) else null,
                        onClick = { vm.setCategory(cat.id) }
                    )
                }
                item {
                    PillButton(
                        text = "新建",
                        icon = Icons.Filled.Add,
                        onClick = { showCategoryDialog = true }
                    )
                }
            }

            // ---------- 卡片颜色 ----------
            SectionLabel("卡片颜色")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EventPalette.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { vm.setColor(index) }
                            .then(
                                if (state.colorIndex == index) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.colorIndex == index) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ---------- 提醒 ----------
            SectionLabel("提醒")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("到日子提醒我", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (state.reminderEnabled) {
                            reminderSummary(state.reminderAdvanceDays, state.reminderHour, state.reminderMinute)
                        } else {
                            "开启后会发通知，需要允许通知权限"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.reminderEnabled,
                    onCheckedChange = { enabled ->
                        vm.setReminderEnabled(enabled)
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            if (state.reminderEnabled) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(4) { index ->
                        val advance = listOf(0, 1, 3, 7)[index]
                        PillButton(
                            text = if (advance == 0) "当天" else "提前 $advance 天",
                            selected = state.reminderAdvanceDays == advance,
                            onClick = { vm.setReminderAdvance(advance) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.reminderHour.toString().padStart(2, '0'),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.toIntOrNull()?.let { h ->
                                if (h in 0..23) vm.setReminderTime(h, state.reminderMinute)
                            }
                        },
                        label = { Text("时") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = state.reminderMinute.toString().padStart(2, '0'),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.toIntOrNull()?.let { m ->
                                if (m in 0..59) vm.setReminderTime(state.reminderHour, m)
                            }
                        },
                        label = { Text("分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // ---------- 描述 ----------
            SectionLabel("备注")
            OutlinedTextField(
                value = state.description,
                onValueChange = vm::setDescription,
                placeholder = { Text("写点什么…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(14.dp),
                maxLines = 5
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { vm.setDate(it) }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addCategory(categoryName)
                    categoryName = ""
                    showCategoryDialog = false
                }, enabled = categoryName.isNotBlank()) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun reminderSummary(advanceDays: Int, hour: Int, minute: Int): String {
    val whenText = if (advanceDays == 0) "当天" else "提前 $advanceDays 天"
    return "$whenText ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )
}
