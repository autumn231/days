package com.example.countdowndays.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.height
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.countdowndays.CountdownApp
import com.example.countdowndays.MainActivity
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.theme.eventColor
import kotlin.math.abs

/**
 * 桌面小组件：显示最近的三个日子。
 * 不让用户配置，默认展示"最近"的三个，避免第一次添加时一片空白。
 */
private val Bg = androidx.compose.ui.graphics.Color(0xFFFDFBF7)
private val TextMain = androidx.compose.ui.graphics.Color(0xFF1F1B16)
private val TextDim = androidx.compose.ui.graphics.Color(0xFF8C8173)
private val Accent = androidx.compose.ui.graphics.Color(0xFFE0612E)

class DaysWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CountdownApp
        val rows = runCatching {
            app.container.repository.getActiveEvents()
                .map { it.event }
                .map { it to it.occurrence() }
                .sortedWith(
                    compareBy(
                        { if (it.first.isPinned) 0 else 1 },
                        { it.second.daysToNext ?: Long.MAX_VALUE }
                    )
                )
                .take(3)
        }.getOrDefault(emptyList())

        provideContent {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Bg))
                        .padding(14.dp)
                        .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "倒数日",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Accent)
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))

                    if (rows.isEmpty()) {
                        Text(
                            text = "还没有记录的日子\n打开应用添加一个吧",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ColorProvider(TextDim)
                            )
                        )
                    } else {
                        rows.forEach { (event, occ) ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = event.name,
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight(),
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = ColorProvider(TextMain)
                                    )
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    text = when {
                                        occ.ended -> "结束"
                                        occ.isToday -> "今天"
                                        else -> "${abs(occ.daysToNext ?: 0L)} 天"
                                    },
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(eventColor(event.colorIndex))
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }
}

class DaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaysWidget()
}
