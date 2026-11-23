package com.example.countdowndays.widget

import android.content.Context

/**
 * 主视觉组件的刷新通道集（v4.5.4 经典版）。
 *
 * v4.5.3 时代刷新收敛在 Glance update() 上，它在用户 HyperOS 上静默失效
 * （五版实证），v4.5.4 起渲染层换成经典 RemoteViews，本对象随之只剩两条
 * 通道，都直达 [HeroWidgetRenderer]：
 *
 * 1. **直接推送**：[refreshAll] 逐放置 id 组装 RemoteViews 同步
 *    updateAppWidget——诊断端实证配置保存、后台闹钟等上下文全部即时出帧；
 * 2. **广播通道**：[sendUpdateBroadcast] 向 [HeroClassicWidgetProvider]
 *    发显式 ACTION_APPWIDGET_UPDATE，走宿主 onUpdate 同款路径作并行保险。
 *
 * 寻址口径不变（v4.5 起）：只认 AppWidgetManager 登记的实际放置 id，
 * 永不用配置界面给的 id。通道失败只记日志、不抛给调用方——刷新失败
 * 不许连带保存、排程与配置返回（见配置界面的解耦顺序）。
 */
internal object HeroWidgetRefresher {

    /** 系统登记的实际放置 id（刷新寻址的唯一口径） */
    fun placedIds(context: Context): List<Int> = HeroWidgetRenderer.placedIds(context)

    /** 广播通道：显式更新广播直达 Provider 的 onUpdate */
    fun sendUpdateBroadcast(context: Context) =
        HeroWidgetRenderer.sendUpdateBroadcast(context)

    /** 直接推送通道：逐个实例同步推送一帧（[source] 只写进日志分清来源） */
    suspend fun refreshAll(context: Context, source: String) =
        HeroWidgetRenderer.renderAll(context, source)
}
