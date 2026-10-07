package com.shahgul.rawphotoconverter.ui

import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Honours system animator scale so reduced-motion / animations-off is immediate. */
@Composable
internal fun motionMillis(normal: Int): Int {
    val context = LocalContext.current
    return remember(context, normal) {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) {
            1f
        }
        if (scale == 0f) 0 else (normal * scale).toInt().coerceAtLeast(0)
    }
}

@Composable
internal fun <T> motionTween(durationMs: Int = 220) = tween<T>(durationMillis = motionMillis(durationMs))
