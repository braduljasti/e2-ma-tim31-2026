package com.example.slagalica.model

enum class NotificationCategory {
    CHAT, RANK, REWARDS, OTHER
}

data class AppNotification(
    val id: Long,
    val title: String,
    val content: String,
    val category: NotificationCategory,
    val timestampMs: Long,
    var read: Boolean = false
) {
    fun relativeTime(): String {
        val diffMs = System.currentTimeMillis() - timestampMs
        val diffMin = diffMs / 60_000
        val diffHour = diffMin / 60
        return when {
            diffMin < 1 -> "Upravo sada"
            diffMin < 60 -> "Prije $diffMin min"
            diffHour < 24 -> "Prije $diffHour h"
            else -> "Prije ${diffHour / 24} d"
        }
    }

    fun emoji(): String = when (category) {
        NotificationCategory.CHAT -> "💬"
        NotificationCategory.RANK -> "🏆"
        NotificationCategory.REWARDS -> "🎁"
        NotificationCategory.OTHER -> "🔔"
    }
}

enum class NotificationFilter { ALL, READ, UNREAD }

data class SkockoAttempt(
    val combination: List<SkockoSymbol>,
    val correctPosition: Int,
    val wrongPosition: Int
)

enum class SkockoSymbol(val emoji: String) {
    SQUARE("■"),
    CIRCLE("●"),
    HEART("♥"),
    TRIANGLE("▲"),
    STAR("★"),
    DIAMOND("◆");

    companion object {
        fun all() = values().toList()
    }
}

data class StepData(
    val id: Long,
    val targetWord: String,
    val hints: List<String>
)

data class MyNumberData(
    val targetNumber: Int,
    val availableNumbers: List<Int>
) {
    companion object {
        fun randomTarget(): Int = (100..999).random()

        fun randomNumbers(): List<Int> {
            val singleDigit = (1..9).shuffled().take(4)
            val medium = listOf(10, 15, 20).random()
            val large = listOf(25, 50, 75, 100).random()
            return (singleDigit + medium + large).shuffled()
        }
    }
}
