package com.example.countdowndays.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.countdowndays.CountdownApp
import com.example.countdowndays.MainActivity
import com.example.countdowndays.R
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.Anniversary
import kotlin.math.abs

/**
 * 桌面小组件（列表款）：显示最近的三个日子。
 * 不让用户配置，默认展示"最近"的三个，避免第一次添加时一片空白。
 * v3.23 视觉升级：圆角卡面、每行强调色条、已过去事件带纪念日预告行。
 */
class DaysWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CountdownApp
        val rows = runCatching {
            app.container.repository.getActiveEventsLight()
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
                        .background(ColorProvider(R.color.widget_bg))
                        .cornerRadius(24.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "倒数日",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(R.color.widget_accent)
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))

                    if (rows.isEmpty()) {
                        Text(
                            text = "还没有记录的日子\n打开应用添加一个吧",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ColorProvider(R.color.widget_text_dim)
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
                                Box(
                                    modifier = GlanceModifier
                                        .width(4.dp)
                                        .height(22.dp)
                                        .background(ColorProvider(eventColor(event.colorIndex)))
                                        .cornerRadius(2.dp)
                                ) {}
                                Spacer(GlanceModifier.width(10.dp))
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = event.name,
                                        maxLines = 1,
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            color = ColorProvider(R.color.widget_text_main)
                                        )
                                    )
                                    // 已过去的一次性事件：纪念日预告
                                    val teaser = if (!occ.isRepeating && (occ.daysToNext ?: 0L) < 0) {
                                        Anniversary.nextFor(event)
                                    } else null
                                    if (teaser != null) {
                                        Text(
                                            text = "距离${teaser.label}还有 ${teaser.daysUntil} 天",
                                            maxLines = 1,
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                color = ColorProvider(R.color.widget_text_dim)
                                            )
                                        )
                                    }
                                }
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
