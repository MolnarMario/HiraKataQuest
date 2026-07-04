package com.hive.hiragana.domain.model

/**
 * Self-assessment after tracing a character, in the order the action bar
 * renders the buttons (worst recall first, like standard SRS apps).
 *
 * Phase 3 extended the Phase 1 set (EASY/OKAY/HARD) to the four
 * simplified-SM-2 grades; rows persisted before the change stored "OKAY",
 * which decodes to [GOOD] (see RoomPracticeRepository).
 */
enum class Grade(val label: String) {
    AGAIN("Again"),
    HARD("Hard"),
    GOOD("Good"),
    EASY("Easy"),
}
