package com.hive.hiragana.domain.model

/**
 * The app-wide light/dark appearance the learner chose.
 *
 * - [SYSTEM] — follow the device's dark-mode setting.
 * - [LIGHT] — always the warm-paper light theme. THE DEFAULT: dark mode is
 *   opt-in, so existing installs look exactly as before until the learner
 *   picks otherwise.
 * - [DARK] — always the sumi/night-washi dark theme, including the writing
 *   paper itself (dark sheet, dimmed ghost/grid, chalk default ink).
 *
 * App-controlled (android:forceDarkAllowed stays false — see
 * res/values/themes.xml): the platform never auto-inverts anything.
 * Persisted via [com.hive.hiragana.domain.repository.SettingsRepository].
 */
enum class AppearanceMode(val prefValue: String, val displayLabel: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        /** Decodes a stored [prefValue]; unknown/null → [LIGHT] (the default). */
        fun fromPrefValue(value: String?): AppearanceMode =
            entries.firstOrNull { it.prefValue == value } ?: LIGHT
    }
}
