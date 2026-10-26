package com.example.data

import com.example.StudyStats
import com.example.McqField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.example.utils.DateUtils

data class DailyActivityStats(val answersToday: Int, val streakCount: Int, val lastActiveTime: Long)

class AnalyticsService(private val statsRepository: StatsRepository) {

    fun computeStats(all: List<McqField>, selected: Map<String, String>): StudyStats {
        if (selected.isEmpty()) return StudyStats()
        val correct = all.count { q ->
            q.correct_answer.equals(selected[q.question], ignoreCase = true)
        }
        val percent = (correct * 100) / selected.size
        return StudyStats(selected.size, correct, percent)
    }

    fun trackAnswerActivity(): DailyActivityStats {
        val today = DateUtils.getTodayDateString()
        val lastDate = statsRepository.getLastAnsweredDate()
        val yesterday = DateUtils.getYesterdayDateString()

        var currentAnswers = statsRepository.getAnswersToday()
        var currentStreak = statsRepository.getStreakCount()

        if (lastDate != today) {
            if (lastDate == yesterday) {
                currentStreak += 1
            } else if (lastDate.isNotEmpty()) {
                currentStreak = 1
            }
            currentAnswers = 1
            statsRepository.saveLastAnsweredDate(today)
            statsRepository.saveStreakCount(currentStreak)
            statsRepository.saveAnswersToday(currentAnswers)
        } else {
            currentAnswers += 1
            statsRepository.saveAnswersToday(currentAnswers)
        }

        val now = System.currentTimeMillis()
        statsRepository.saveLastActiveTime(now)

        return DailyActivityStats(
            answersToday = currentAnswers,
            streakCount = currentStreak,
            lastActiveTime = now
        )
    }

    fun generateNotifications(streak: Int, solved: Int, goal: Int, lastActiveTime: Long): List<com.example.AppNotification> {
        val list = mutableListOf<com.example.AppNotification>()

        // 1. Goal Notification
        if (solved >= goal) {
            list.add(
                com.example.AppNotification(
                    id = "goal_status",
                    title = "Daily Goal Achieved! 🎉",
                    text = "Fantastic job! You've completed $solved/$goal MCQs today. Keep it up!",
                    category = "goal",
                    urgency = com.example.NotificationUrgency.LOW,
                    actionLabel = "See Analytics",
                    actionType = "see_stats"
                )
            )
        } else {
            val remaining = goal - solved
            list.add(
                com.example.AppNotification(
                    id = "goal_status",
                    title = "Daily Goal Progress ($solved/$goal) 🩺",
                    text = "You are $remaining MCQs away from your daily goal. Consistent practice is key to clinical mastery!",
                    category = "goal",
                    urgency = if (solved > 0) com.example.NotificationUrgency.MEDIUM else com.example.NotificationUrgency.HIGH,
                    actionLabel = "Start Studying",
                    actionType = "practice_subject"
                )
            )
        }

        // 2. Streak Notification
        if (streak > 0) {
            list.add(
                com.example.AppNotification(
                    id = "streak_status",
                    title = "Study Streak Active! 🔥",
                    text = "You're on a $streak-day study streak. Keep the momentum going!",
                    category = "streak",
                    urgency = com.example.NotificationUrgency.LOW,
                    actionLabel = "Keep Building",
                    actionType = "practice_subject"
                )
            )
        } else {
            list.add(
                com.example.AppNotification(
                    id = "streak_status",
                    title = "Start Your Streak! 📈",
                    text = "A blank slate is a perfect opportunity. Answer questions today to start your streak!",
                    category = "streak",
                    urgency = com.example.NotificationUrgency.MEDIUM,
                    actionLabel = "Practice Now",
                    actionType = "practice_subject"
                )
            )
        }

        return list
    }
}
