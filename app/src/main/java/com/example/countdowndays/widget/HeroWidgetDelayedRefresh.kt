package com.example.countdowndays.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * 主视觉组件「首次添加竞态」的延迟兜底重渲染（v4.4）。
 *
 * v4.3 云真机复验 6 次选择对了 5 次，唯一的尾巴出在首个组件的首次添加：
 * 配置界面选了 100 天的事件，桌面首帧却显示自动口径的 3 天事件，删掉重加
 * 又正常、再未复现。判断是首次添加链路上的竞态——系统在配置流程完成前后
 * 会触发一次初始渲染，若它抢在配置落盘/回落判定生效之前跑完，就会按自动
 * 口径出一帧，而且没有任何后续事件来纠正它（只能等 30 分钟周期更新或
 * 下一次改选）。改选链路没有这个问题，所以本版只加固首次添加：
 *
 * 1) 配置保存后（HeroWidgetConfigureActivity.confirmSelection）在原有三路
 *    刷新之外，再排一次 3 秒后的兜底重渲染；
 * 2) 组件首次启用侧（HeroDaysWidgetReceiver.onEnabled 与每个实例的首次
 *    onUpdate）也各排一次同样的延迟自检。
 *
 * 延迟机制走 WorkManager 一次性任务：配置 Activity / Receiver 所在进程
 * 随时可能被系统杀掉（HyperOS 尤其积极），进程内的 Handler/协程延迟活不
 * 下来；WorkManager 的任务由系统持久化调度，进程死了照样触发。
 *
 * 兜底重渲染只走 Glance update()、不发系统更新广播、不经 Receiver.onUpdate，
 * 不会反过来再排一份延迟任务，没有自激循环；且本身幂等——配置已落盘就按
 * 配置出帧、没配置就按自动口径出帧，都不出错。日志沿用 HeroWidgetCfg /
 * HeroWidgetRender 两个 tag 并注明 delayed 与来源，方便抓日志时区分。
 */
internal object HeroDelayedRefresh {

    /** 延迟秒数：够配置落盘与回落判定生效，又不至于让用户盯着错的一帧太久 */
    const val DELAY_SECONDS = 3L

    /** 触发来源，写进日志与任务名，抓日志时能分清是哪条链路排的 */
    enum class Source { CONFIGURE_SAVE, FIRST_ENABLE, FIRST_UPDATE }

    /** 唯一任务名：同一来源＋同一实例只留一份（REPLACE 覆盖），不同实例互不影响 */
    fun uniqueWorkName(source: Source, appWidgetId: Int?): String =
        "hero_delayed_refresh_${source.name}_${appWidgetId ?: "all"}"

    /**
     * 调度决策（纯函数，可单测）：配置保存与首次启用无条件排——它们本来就
     * 一次性；首次 onUpdate 只对没见过的实例排，周期更新每 30 分钟都来，
     * 不能每次都跟着排一份。
     */
    fun shouldSchedule(source: Source, alreadySeen: Boolean): Boolean = when (source) {
        Source.CONFIGURE_SAVE -> true
        Source.FIRST_ENABLE -> true
        Source.FIRST_UPDATE -> !alreadySeen
    }

    /**
     * 延迟重渲染实际刷哪些实例（纯函数，可单测）：
     * 目标 id 在系统登记的放置列表里 → 只刷它；不在 → 刷全部已放置实例
     * （配置界面的 id 与放置 id 对不上正是 v4.3 的病根，这种时候按 id 刷
     * 必然刷空）；一个都没放置但 id 已知 → 仍试刷这个 id（刚添加、登记
     * 列表还没刷新到的窗口期）；全无信息 → 空，不刷。
     */
    fun renderTargets(appWidgetId: Int?, placedIds: IntArray): List<Int> = when {
        placedIds.isEmpty() -> listOfNotNull(appWidgetId)
        appWidgetId != null && appWidgetId in placedIds -> listOf(appWidgetId)
        else -> placedIds.toList()
    }

    /** 排一次延迟兜底重渲染；失败只记日志——原有刷新路径与周期更新仍在兜底 */
    fun schedule(context: Context, source: Source, appWidgetId: Int?) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<HeroDelayedRefreshWorker>()
                .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        HeroDelayedRefreshWorker.KEY_SOURCE to source.name,
                        HeroDelayedRefreshWorker.KEY_APPWIDGET_ID to (appWidgetId ?: -1)
                    )
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    uniqueWorkName(source, appWidgetId),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            Log.i(
                "HeroWidgetCfg",
                "delayed 已排兜底重渲染 source=$source appWidgetId=$appWidgetId " +
                    "delay=${DELAY_SECONDS}s"
            )
        }.onFailure {
            Log.w(
                "HeroWidgetCfg",
                "delayed 排兜底重渲染失败 source=$source appWidgetId=$appWidgetId",
                it
            )
        }
    }
}

/** 延迟兜底重渲染的执行体：到点按当前已落盘配置重出一帧，逻辑见 HeroDelayedRefresh */
class HeroDelayedRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(KEY_SOURCE) ?: "?"
        val appWidgetId = inputData.getInt(KEY_APPWIDGET_ID, -1).takeIf { it > 0 }
        val placedIds = runCatching {
            AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(
                ComponentName(applicationContext, HeroDaysWidgetReceiver::class.java)
            )
        }.getOrDefault(IntArray(0))
        val targets = HeroDelayedRefresh.renderTargets(appWidgetId, placedIds)
        Log.i(
            "HeroWidgetRender",
            "delayed 兜底重渲染触发 source=$source appWidgetId=$appWidgetId " +
                "placed=${placedIds.toList()} targets=$targets"
        )
        targets.forEach { id ->
            runCatching {
                HeroDaysWidget().update(applicationContext, AppWidgetId(id))
            }.onFailure {
                Log.w("HeroWidgetRender", "delayed 兜底重渲染失败 id=$id", it)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_SOURCE = "source"
        const val KEY_APPWIDGET_ID = "app_widget_id"
    }
}
