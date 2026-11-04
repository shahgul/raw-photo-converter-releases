// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object CompletionFeedback {
    fun trigger(
        context: Context,
        hasFailures: Boolean,
        hapticsEnabled: Boolean = true,
        soundEnabled: Boolean = true,
    ) {
        if (hapticsEnabled) {
            triggerHaptic(context, hasFailures)
        }
        if (soundEnabled) {
            triggerSound(context)
        }
    }

    private fun triggerHaptic(context: Context, hasFailures: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (hasFailures) {
                    val timings = longArrayOf(0, 90, 70, 90)
                    val amplitudes = intArrayOf(0, 180, 0, 180)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (hasFailures) 200L else 45L)
            }
        } catch (error: Exception) {
            Log.w("CompletionFeedback", "Haptic feedback unavailable", error)
        }
    }

    private fun triggerSound(context: Context) {
        try {
            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, alert)
            ringtone?.play()
        } catch (error: Exception) {
            Log.w("CompletionFeedback", "Audio feedback unavailable or muted", error)
        }
    }
}
