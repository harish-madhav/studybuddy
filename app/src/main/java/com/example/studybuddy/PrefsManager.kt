
// File: PrefsManager.kt
package com.example.studybuddy

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveFocusDuration(minutes: Int) {
        prefs.edit().putInt(KEY_FOCUS_DURATION, minutes).apply()
    }

    fun saveBreakDuration(minutes: Int) {
        prefs.edit().putInt(KEY_BREAK_DURATION, minutes).apply()
    }

    fun saveTotalFocusMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_TOTAL_FOCUS_MINUTES, minutes).apply()
    }

    fun getFocusDuration(): Int {
        return prefs.getInt(KEY_FOCUS_DURATION, DEFAULT_FOCUS_DURATION)
    }

    fun getBreakDuration(): Int {
        return prefs.getInt(KEY_BREAK_DURATION, DEFAULT_BREAK_DURATION)
    }

    fun getTotalFocusMinutes(): Int {
        return prefs.getInt(KEY_TOTAL_FOCUS_MINUTES, 0)
    }

    companion object {
        private const val PREFS_NAME = "study_buddy_prefs"
        private const val KEY_FOCUS_DURATION = "focus_duration"
        private const val KEY_BREAK_DURATION = "break_duration"
        private const val KEY_TOTAL_FOCUS_MINUTES = "total_focus_minutes"

        private const val DEFAULT_FOCUS_DURATION = 1  //25
        private const val DEFAULT_BREAK_DURATION = 1  //5
    }
}