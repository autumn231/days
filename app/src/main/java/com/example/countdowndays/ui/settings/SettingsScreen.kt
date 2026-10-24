package com.example.countdowndays.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.countdowndays.ui.common.PillButton
import com.example.countdowndays.util.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenDateCalc: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = com.example.countdowndays.ui.common.appViewModel {
        SettingsViewModel(it.repository, it.prefs)
    }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val dynamic by vm.dynamicColor.collectAsStateWithLifecycle()

    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var lastExportOk by remember { mutableStateOf<Boolean?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = vm.exportBackup(context, uri)
                Toast.makeText(
                    context,
                    if (ok) "备份已保存" else "备份失败",
                    Toast.LENGTH_SHORT
                ).show()
                lastExportOk = ok
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------- 外观 ----------
            SettingsCard(title = "外观", icon = Icons.Filled.Brightness6) {
                Text(
                    text = "主题模式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        text = "跟随系统",
                        selected = theme == ThemeMode.SYSTEM,
                        onClick = { vm.setThemeMode(ThemeMode.SYSTEM) }
                    )
                    PillButton(
                        text = "浅色",
                        selected = theme == ThemeMode.LIGHT,
                        onClick = { vm.setThemeMode(ThemeMode.LIGHT) }
                    )
                    PillButton(
                        text = "深色",
                        selected = theme == ThemeMode.DARK,
                        onClick = { vm.setThemeMode(ThemeMode.DARK) }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "从壁纸提取配色（Android 12+）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dynamic, onCheckedChange = vm::setDynamicColor)
                }
            }

            // ---------- 数据 ----------
            SettingsCard(title = "数据", icon = Icons.Filled.Save) {
                ActionRow(
                    icon = Icons.Filled.Save,
                    title = "导出备份",
                    subtitle = "把事件、时间线和配图打包成 zip（不上传服务器）",
                    onClick = {
                        exportLauncher.launch("days-backup-${System.currentTimeMillis()}.zip")
                    }
                )
                ActionRow(
                    icon = Icons.Filled.Restore,
                    title = "导入备份",
                    subtitle = "从 zip 恢复，重复事件会追加",
                    onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) }
                )
                ActionRow(
                    icon = Icons.Filled.Archive,
                    title = "归档事件",
                    subtitle = "查看已归档的日子",
                    onClick = onOpenArchive
                )
            }

            // ---------- 工具 ----------
            SettingsCard(title = "工具", icon = Icons.Filled.Calculate) {
                ActionRow(
                    icon = Icons.Filled.Calculate,
                    title = "日期计算",
                    subtitle = "算两个日期之间隔了多少天",
                    onClick = onOpenDateCalc
                )
                ActionRow(
                    icon = Icons.Filled.Palette,
                    title = "关于",
                    subtitle = "版本信息与数据来源",
                    onClick = onOpenAbout
                )
            }

            Text(
                text = "提示：提醒功能在每个事件的编辑页里单独开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImportConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入备份？") },
            text = { Text("现有数据不会被删除，备份中的事件会追加到列表中。") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    showImportConfirm = false
                    if (uri != null) {
                        scope.launch {
                            val count = vm.importBackup(context, uri)
                            Toast.makeText(
                                context,
                                if (count > 0) "已导入 $count 个事件" else "导入失败或文件为空",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}
