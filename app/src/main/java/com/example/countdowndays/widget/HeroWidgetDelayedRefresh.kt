package com.example.countdowndays.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.countdowndays.CountdownApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 主视觉组件「首次添加竞态」的延迟兜底重渲染。
 *
 * v4.4 用 WorkManager 排 3 秒延迟任务，云真机实测未生效（首帧错了之后等了
 * 2 分钟也没纠正过来）。复盘结论：WorkManager 一次性任务只有「最终会执行」
 * 的语义，没有时效保证——initialDelay 在 Doze / 应用待机桶下可以被推迟到
 * 系统维护窗口（分钟级），且排程失败被静默吞掉后无从取证。把它当唯一的
 * 纠正链，链路长、不可观测、时效不受控。
 *
 * v4.5 换成 AlarmManager（与提醒功能 ReminderScheduler 同一套机制，本应用
 * 已声明 SCHEDULE_EXACT_ALARM 并在真机验证过）：能精确就 setExactAndAllow
 * WhileIdle、不能就 setAndAllowWhileIdle，到点由系统拉起
 * [HeroDelayedRefreshReceiver]，按系统登记的**实际放置 id**逐个重渲染。
 * 链路短（闹钟 → 广播 → update），时效可控，进程被杀也照样触发。
 *
 * 寻址底线（v4.5）：刷新只用 AppWidgetManager 登记的实际放置 id，永远不用
 * 配置界面给的 id 寻址——v4.3 已实测两端 id 值域对不上，按配置 id 刷必然
 * 静默打空。所有来源共用同一个 PendingIntent，后排覆盖先排（天然去重），
 * 所以调用方不需要再做「只排一次」的标记（v4.4 的 seen 标记有个隐患：
 * 先置标记再排程，排程失败一次就被永久跳过，本版连同标记一并去掉）。
 *
 * 兜底重渲染只走经典直推（HeroWidgetRefresher.refreshAll）、不经 Provider.onUpdate，
 * 不会反过来再排一份闹钟，没有自激循环；且本身幂等——渲染决策是自洽的
 * （见 HeroDaysWidget.resolveHeroForInstance），没配置就按自动口径出帧。
 * 日志沿用 HeroWidgetCfg / HeroWidgetRender 并注明 delayed 与来源。
 *
 * v4.5.3：配置保存不再排这条 +3s 闹钟，改排 [HeroReceiptCheck] 的回执
 * 检查（+7s、独立 requestCode 4502）。此前三个来源共用同一个
 * PendingIntent（4501），周期 onUpdate 排的 FIRST_UPDATE 会把保存的
 * 兜底顶掉；本版 4501 只留给 FIRST_ENABLE / FIRST_UPDATE，保存链有
 * 自己独立的编号，互不覆盖。
 */
internal object HeroDelayedRefresh {

    /** 延迟秒数：够配置落盘与回落判定生效，又不至于让用户盯着错的一帧太久 */
    const val DELAY_SECONDS = 3L

    /** 闹钟广播的 action（显式 Intent 直达 Receiver，action 只作双保险校验） */
    const val ACTION = "com.example.countdowndays.action.HERO_DELAYED_REFRESH"

    /** 闹钟 PendingIntent 的请求码：全应用唯一一个延迟重渲染闹钟 */
    private const val REQUEST_CODE = 4501

    /** 触发来源，只写进日志，抓日志时能分清是哪条链路排的 */
    enum class Source { CONFIGURE_SAVE, FIRST_ENABLE, FIRST_UPDATE }

    /**
     * 延迟重渲染实际刷哪些实例（纯函数，可单测）：只认系统登记的实际放置
     * id、逐个全刷；一个都没放置就不刷（按任何 id 刷都不可见，白费）。
     * 配置侧 id 永不参与寻址，所以这里根本不收那个参数。
     */
    fun renderTargets(placedIds: IntArray): List<Int> = placedIds.toList()

    /** 排一次延迟兜底重渲染；失败只记日志——渲染自洽＋周期更新仍在兜底 */
    fun schedule(context: Context, source: Source) {
        runCatching {
            val appContext = context.applicationContext
            val alarms = appContext.getSystemService(AlarmManager::class.java)
                ?: error("AlarmManager 不可用")
            val triggerAt = System.currentTimeMillis() + DELAY_SECONDS * 1000L
            val exact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                alarms.canScheduleExactAlarms()
            if (exact) {
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext)
                )
            } else {
                alarms.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext)
                )
            }
            Log.i(
                "HeroWidgetCfg",
                "delayed 已排兜底重渲染(alarm) source=$source delay=${DELAY_SECONDS}s " +
                    "exact=$exact"
            )
        }.onFailure {
            Log.w("HeroWidgetCfg", "delayed 排兜底重渲染失败 source=$source", it)
        }
    }

    /** 唯一的闹钟 PendingIntent：同一实例，后排的闹钟天然顶掉先排的 */
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HeroDelayedRefreshReceiver::class.java)
            .setAction(ACTION)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 延迟兜底重渲染的闹钟接收器：到点按实际放置 id 逐个重渲染，逻辑见 HeroDelayedRefresh */
class HeroDelayedRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HeroDelayedRefresh.ACTION) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val placedIds = HeroWidgetRefresher.placedIds(appContext)
                val targets = HeroDelayedRefresh.renderTargets(placedIds.toIntArray())
                Log.i(
                    "HeroWidgetRender",
                    "delayed 兜底重渲染触发(alarm) placed=$placedIds targets=$targets"
                )
                // 实际刷新走共享通道集（HeroWidgetRefresher）：经典直推
                HeroWidgetRefresher.refreshAll(appContext, "闹钟延迟刷新")
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * 渲染回执自检（v4.5.3 新增）：配置保存后 +7 秒的一次性核对。
 *
 * 背景：v4.5.2 真机取证定案断点在「保存 → 触发重渲染」——保存侧全对，
 * 但主动刷新通道在 HyperOS 上静默失效，保存后没有任何新渲染。光把通
 * 道加多还不够，失败必须能**自愈**：保存时配置界面已把期望（保存时刻
 * ＋渲染应解析出的选择）记进偏好（见 PrefsManager 的
 * HeroRefreshExpectation），每次渲染也只记一份最小结果（认到的选择
 * ＋时刻）；本闹钟到点拿两者比对（[evaluateHeroReceipt]）：
 *
 * - 达标 → 什么都不做；
 * - 未达标/无渲染记录 → 升级走广播通道（宿主 onUpdate 同款路径）
 *   再刷一次＋经典直推兜底。
 *
 * 闹钟与 [HeroDelayedRefresh] 完全分离：独立 action、独立 requestCode
 * （4502），不再出现「周期 onUpdate 把保存兜底顶掉」的共用覆盖。检查
 * 结论只记日志，不留任何诊断痕迹（v4.5.2 的诊断代码已整体删除）。
 */
internal object HeroReceiptCheck {

    /** 检查延迟：给即时刷新留足出帧时间，又不让用户盯着错帧太久 */
    const val DELAY_SECONDS = 7L

    /** 回执检查广播的 action（显式 Intent 直达 Receiver，只作双保险校验） */
    const val ACTION = "com.example.countdowndays.action.HERO_RECEIPT_CHECK"

    /** 独立请求码：与 HeroDelayedRefresh 的 4501 分开，互不覆盖 */
    private const val REQUEST_CODE = 4502

    /** 排一次回执检查；失败只记日志——即时刷新通道仍是主送达路径 */
    fun schedule(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val alarms = appContext.getSystemService(AlarmManager::class.java)
                ?: error("AlarmManager 不可用")
            val triggerAt = System.currentTimeMillis() + DELAY_SECONDS * 1000L
            val exact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                alarms.canScheduleExactAlarms()
            if (exact) {
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext)
                )
            } else {
                alarms.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext)
                )
            }
            Log.i(
                "HeroWidgetCfg",
                "receipt 已排回执检查(alarm) delay=${DELAY_SECONDS}s exact=$exact"
            )
        }.onFailure {
            Log.w("HeroWidgetCfg", "receipt 排回执检查失败", it)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HeroReceiptCheckReceiver::class.java)
            .setAction(ACTION)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 回执检查的闹钟接收器：到点比对最近渲染记录，未达标就升级广播再刷 */
class HeroReceiptCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HeroReceiptCheck.ACTION) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val prefs = (appContext as CountdownApp).container.prefs
                val expectation = prefs.heroRefreshExpectation()
                val render = prefs.heroLastRender()
                val verdict = evaluateHeroReceipt(expectation, render)
                Log.i(
                    "HeroWidgetRender",
                    "receipt 回执检查 verdict=$verdict 期望=$expectation 最近渲染=$render"
                )
                if (verdict != HeroReceiptVerdict.MET) {
                    // 未达标/无渲染记录：升级走广播通道＋经典直推再刷一次
                    HeroWidgetRefresher.sendUpdateBroadcast(appContext)
                    HeroWidgetRefresher.refreshAll(appContext, "回执升级")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
