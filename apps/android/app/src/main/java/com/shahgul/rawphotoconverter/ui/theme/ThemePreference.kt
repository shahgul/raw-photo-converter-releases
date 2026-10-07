package com.shahgul.rawphotoconverter.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class ThemePreference {
    System, Light, Dark;

    fun next(): ThemePreference = when (this) {
        System -> Light
        Light -> Dark
        Dark -> System
    }

    val label: String
        get() = when (this) {
            System -> "System"
            Light -> "Light"
            Dark -> "Dark"
        }
}

private const val PREFS = "ui"
private const val KEY_THEME = "theme_preference"

fun readThemePreference(context: Context): ThemePreference {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, ThemePreference.System.name)
    return runCatching { ThemePreference.valueOf(raw ?: ThemePreference.System.name) }.getOrDefault(ThemePreference.System)
}

fun writeThemePreference(context: Context, preference: ThemePreference) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, preference.name)
        .apply()
}

@Composable
fun rememberThemePreference(): Pair<ThemePreference, (ThemePreference) -> Unit> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readThemePreference(context)) }
    return state.value to { next ->
        writeThemePreference(context, next)
        state.value = next
    }
}

@Composable
fun ThemePreference.resolvesDark(): Boolean = when (this) {
    ThemePreference.System -> isSystemInDarkTheme()
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
}
