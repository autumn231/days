package com.example.countdowndays.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.updateAll

/**
 * 主视觉组件的刷新通道集（v4.5.3）。
 *
 * v4.5.2 真机取证＋代码审计定案：此前应用主动刷新全部收敛到同一句
 * Glance update()，它在用户 HyperOS 上静默失效时，即时刷新与闹钟兜底
 * 一起死，只剩宿主驱动的 onUpdate（30 分钟周期/应用更新）能出帧。
 * 本版把刷新拆成多条独立通道：
 *
 * 1. **广播通道（主）**：[sendUpdateBroadcast] 向 [HeroDaysWidgetReceiver]
 *    发显式 ACTION_APPWIDGET_UPDATE 广播，走的正是宿主 onUpdate 同款
 *    路径——该机上唯一有出帧实证的送达方式；
 * 2. **Glance update()**：[glanceUpdateEach] 按实际放置 id 逐个刷，
 *    保留作并行尝试（别的启动器上它本来就好使）；
 * 3. **updateAll**：[updateAllNow] 全量刷，作补充。
 *
 * 寻址口径不变（v4.5 起）：只认 AppWidgetManager 登记的实际放置 id，
 * 永不用配置界面给的 id。所有通道的失败都只记日志、不抛给调用方——
 * 刷新失败不许连带保存、排程与配置返回（见配置界面的解耦顺序）。
 */
internal object HeroWidgetRefresher {

    /** 系统登记的实际放置 id（刷新寻址的唯一口径） */
    fun placedIds(context: Context): List<Int> = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, HeroDaysWidgetReceiver::class.java)
        ).toList()
    }.getOrDefault(emptyList())

    /** 广播通道（主通道）：显式更新广播直达 Receiver 的 onUpdate */
    fun sendUpdateBroadcast(context: Context) {
        val appContext = context.applicationContext
        val ids = placedIds(appContext)
        if (ids.isEmpty()) {
            Log.i("HeroWidgetRefresh", "广播 跳过（放置列表为空）")
            return
        }
        runCatching {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(ComponentName(appContext, HeroDaysWidgetReceiver::class.java))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids.toIntArray())
            appContext.sendBroadcast(intent)
            Log.i("HeroWidgetRefresh", "广播 已发送 placed=$ids")
        }.onFailure {
            Log.w("HeroWidgetRefresh", "广播 发送失败 placed=$ids", it)
        }
    }

    /** Glance update() 通道：按实际放置 id 逐个刷 */
    suspend fun glanceUpdateEach(context: Context, channel: String) {
        val appContext = context.applicationContext
        val ids = placedIds(appContext)
        if (ids.isEmpty()) {
            Log.i("HeroWidgetRefresh", "$channel 跳过（放置列表为空）")
            return
        }
        ids.forEach { id ->
            runCatching {
                HeroDaysWidget().update(appContext, AppWidgetId(id))
            }.onFailure {
                Log.w("HeroWidgetRefresh", "$channel update() 失败 id=$id", it)
            }
        }
        Log.i("HeroWidgetRefresh", "$channel 已逐个刷新 placed=$ids")
    }

    /** updateAll 通道（补充）：全量刷一次 */
    suspend fun updateAllNow(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            HeroDaysWidget().updateAll(appContext)
            Log.i("HeroWidgetRefresh", "updateAll 已执行")
        }.onFailure {
            Log.w("HeroWidgetRefresh", "updateAll 失败", it)
        }
    }
}
