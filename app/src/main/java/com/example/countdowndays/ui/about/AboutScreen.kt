package com.example.countdowndays.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.countdowndays.R

private const val AUTHOR_URL = "https://github.com/autumn231"
// v4.5.3：作者只改展示文案为「秋」，点击跳转目标仍是 GitHub 主页
private const val AUTHOR_DISPLAY = "秋"
private const val REPO_URL = "https://github.com/autumn231/days"
private const val REPO_DISPLAY = "github.com/autumn231/days"
private const val LICENSE_NAME = "MIT License"

/**
 * MIT 许可证通行中文译文（v4.5.5 起改中文）：标题与版权行保留英文原文，
 * 条款内容与仓库根目录 LICENSE 文件（英文原文）一一对应。
 */
private val LICENSE_TEXT = """
MIT License

Copyright (c) 2026 秋

特此免费授予任何获得本软件及相关文档文件（以下简称「软件」）副本的人，不受限制地处理本软件的权利，包括但不限于使用、复制、修改、合并、发布、分发、再许可及／或出售本软件副本的权利，并允许获提供本软件的人这样做，但须满足以下条件：

上述版权声明及本许可声明应包含在本软件的所有副本或重要部分中。

本软件按「原样」提供，不附任何形式的明示或暗示担保，包括但不限于适销性、特定用途适用性及不侵权的担保。在任何情况下，作者或版权持有人均不对由本软件或本软件的使用或其他交易引起、产生或与之相关的任何索赔、损害或其他责任负责，无论是在合同诉讼、侵权行为还是其他诉讼中。
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    var showLicense by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 图标：本体下半截是奶油白（#FFFBF2），与页面近白背景相融看不见
            // 边界（v4.5.5 用户截图实证）——垫一块浅色圆角底衬＋淡描边＋柔和
            // 阴影，把图标轮廓从背景里托出来
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 4.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(96.dp)
                )
            }
            Text(
                text = "倒数日",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (version.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = "  v$version  ",
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = "记录每一个重要的日子，\n倒数或累计，陪伴你的时光。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.size(16.dp))

            // 作者与开源信息卡片（v4.0：移除联系方式，加仓库地址与开源许可；
            // v4.1：作者行改 GitHub 账号 autumn231，点击跳个人主页；
            // v4.5.3：作者显示名改「秋」（链接不变）；v4.5.2 的组件诊断
            // 入口、诊断页与全部诊断代码已整体删除；
            // v4.5.5：作者行加 GitHub 头像（打包进 APK，离线可用）；三行右侧
            // 加跳转标记——作者/仓库是方框斜箭头（浏览器外跳），许可行是
            // 右箭头（应用内打开中文译文弹窗），两种去向一眼可分）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow(
                        icon = Icons.Filled.Person,
                        label = "作者",
                        value = AUTHOR_DISPLAY,
                        external = true,
                        leading = {
                            Image(
                                painter = painterResource(R.drawable.author_avatar),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        },
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(AUTHOR_URL))
                                )
                            }
                        }
                    )
                    InfoRow(
                        icon = Icons.Filled.Code,
                        label = "开源仓库",
                        value = REPO_DISPLAY,
                        external = true,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                                )
                            }
                        }
                    )
                    InfoRow(
                        icon = Icons.Filled.Description,
                        label = "开源许可",
                        value = LICENSE_NAME,
                        onClick = { showLicense = true }
                    )
                }
            }
        }
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text(LICENSE_NAME) },
            text = {
                Text(
                    text = LICENSE_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    external: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(16.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        if (onClick != null) {
            Spacer(Modifier.weight(1f))
            // 右侧去向标记（v4.5.5）：外跳浏览器＝方框斜箭头，应用内打开＝右箭头
            if (external) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = "在浏览器打开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
