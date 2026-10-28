// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
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
            triggerSound(context, hasFailures)
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

    /**
     * Soft sonification on the media stream. ToneGenerator plays asynchronously, so the
     * generator has to stay alive until the tone finishes — releasing it immediately is silent.
     */
    private fun triggerSound(context: Context, hasFailures: Boolean) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mediaAudible = audio?.getStreamVolume(AudioManager.STREAM_MUSIC)?.let { it > 0 } != false
            val stream = if (mediaAudible) AudioManager.STREAM_MUSIC else AudioManager.STREAM_SYSTEM
            val durationMs = if (hasFailures) 220 else 180
            val tone = if (hasFailures) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK
            val generator = ToneGenerator(stream, if (hasFailures) 55 else 48)
            if (!generator.startTone(tone, durationMs)) {
                generator.release()
                return
            }
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { generator.release() }
            }, durationMs + 80L)
        } catch (error: Exception) {
            Log.w("CompletionFeedback", "Audio feedback unavailable or muted", error)
        }
    }
}
