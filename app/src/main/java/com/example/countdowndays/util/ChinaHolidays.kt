package com.example.countdowndays.util

import java.time.LocalDate

/**
 * 中国法定节假日与调休上班日（内置数据，离线可用）。
 *
 * 数据年份与来源（国务院办公厅通知原文，gov.cn）：
 * - 2026 年：《国务院办公厅关于2026年部分节假日安排的通知》国办发明电〔2025〕7号（2025-11-04 公布）
 * - 2027 年：《国务院办公厅关于2027年部分节假日安排的通知》国办发明电〔2026〕10号（2026-11-05 公布）
 * 2028 年及以后官方尚未公布，不在表内（查询返回 null / false，日期选择器不标注）。
 *
 * holidays 收录的是「放假调休期」的整段日期（含拼假的周末），值是节日名；
 * workdays 是被调休改为上班的周末日期。
 */
object ChinaHolidays {

    private val holidays: Map<LocalDate, String> = buildMap {
        fun range(name: String, from: LocalDate, to: LocalDate) {
            var d = from
            while (!d.isAfter(to)) {
                put(d, name)
                d = d.plusDays(1)
            }
        }
        // ---------- 2026 年（国办发明电〔2025〕7号） ----------
        range("元旦", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
        range("春节", LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23))
        range("清明节", LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6))
        range("劳动节", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5))
        range("端午节", LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21))
        range("中秋节", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27))
        range("国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))
        // ---------- 2027 年（国办发明电〔2026〕10号） ----------
        range("元旦", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3))
        range("春节", LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 13))
        range("清明节", LocalDate.of(2027, 4, 3), LocalDate.of(2027, 4, 5))
        range("劳动节", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 5))
        range("端午节", LocalDate.of(2027, 6, 9), LocalDate.of(2027, 6, 9))
        range("中秋节", LocalDate.of(2027, 9, 15), LocalDate.of(2027, 9, 15))
        range("国庆节", LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 7))
    }

    /** 调休上班日（周末被调整为工作日） */
    private val workdays: Set<LocalDate> = setOf(
        // 2026 年
        LocalDate.of(2026, 1, 4),
        LocalDate.of(2026, 2, 14), LocalDate.of(2026, 2, 28),
        LocalDate.of(2026, 5, 9),
        LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 10),
        // 2027 年
        LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 14),
        LocalDate.of(2027, 5, 8),
        LocalDate.of(2027, 9, 26), LocalDate.of(2027, 10, 9)
    )

    /** 该日所属节日名；不在放假期内返回 null */
    fun holidayName(date: LocalDate): String? = holidays[date]

    fun isHoliday(date: LocalDate): Boolean = holidays.containsKey(date)

    /** 是否调休上班日（周末但要上班） */
    fun isAdjustedWorkday(date: LocalDate): Boolean = workdays.contains(date)
}
