package com.example.countdowndays.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.countdowndays.widget.buildHeroDiagnosticDump
import com.example.countdowndays.widget.collectHeroDiagnostics

/**
 * 组件诊断页（v4.5.2 诊断版）：打开时现采一份诊断快照、拼成全文展示，
 * 一键复制到剪贴板。用法：组件不跟随所选事件时，先去改选一次，再回到
 * 这里复制全文发回——保存入参、存储全量、系统放置实例与最近一次渲染
 * 的解析过程都在里面，断点在哪一眼可见，不再靠猜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var dump by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dump = runCatching {
            buildHeroDiagnosticDump(collectHeroDiagnostics(context))
        }.getOrElse { "诊断信息生成失败：${it.message}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("组件诊断", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    val text = dump ?: return@Button
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("倒数日组件诊断", text))
                    copied = true
                },
                enabled = dump != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (copied) "已复制，粘贴发回即可" else "复制诊断信息")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = dump ?: "正在生成…",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
