package com.example.data

import android.content.Context
import android.content.SharedPreferences

class StatsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mcq_prefs", Context.MODE_PRIVATE)

    fun getStreakCount(): Int = prefs.getInt("streak_count", 0)
    fun saveStreakCount(streak: Int) {
        prefs.edit().putInt("streak_count", streak).apply()
    }

    fun getLastActiveTime(): Long = prefs.getLong("last_active_time", 0L)
    fun saveLastActiveTime(time: Long) {
        prefs.edit().putLong("last_active_time", time).apply()
    }

    fun getAnswersToday(): Int = prefs.getInt("answers_today", 0)
    fun saveAnswersToday(count: Int) {
        prefs.edit().putInt("answers_today", count).apply()
    }

    fun getLastAnsweredDate(): String = prefs.getString("last_answered_date", "") ?: ""
    fun saveLastAnsweredDate(dateStr: String) {
        prefs.edit().putString("last_answered_date", dateStr).apply()
    }
}
