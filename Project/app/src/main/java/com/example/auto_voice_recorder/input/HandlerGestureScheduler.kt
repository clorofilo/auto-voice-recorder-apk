package com.example.auto_voice_recorder.input

import android.os.Handler
import android.os.Looper

/**
 * Real [GestureScheduler] backed by a main-thread [Handler]. Not unit
 * tested: it is a thin wrapper over `postDelayed`/`removeCallbacks` with no
 * logic of its own beyond what [VolumeGestureDetector]'s tests already
 * exercise against a fake scheduler.
 */
class HandlerGestureScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : GestureScheduler {

    override fun scheduleAfter(delayMs: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return object : Cancellable {
            override fun cancel() {
                handler.removeCallbacks(runnable)
            }
        }
    }
}
