package com.veatch_tutic.crashrecorder.video_streaming

import android.os.Handler
import android.os.Looper

class VideoStreamingThread(private val threadReadyCallback: VideoStreamingThreadReadyCallback?) : Thread() {
    companion object {
        val MESSAGE_CODE = 0
    }

    lateinit var messageHandler: Handler

    override fun run() {
        Looper.prepare()

        messageHandler = Handler {
            // handle message, will use this to
            System.out.println("received message")
            true
        }

        threadReadyCallback?.onRunning(messageHandler)

        Looper.loop()

        // Spin up streaming video and saving to buffer.

        // Let main activity know message handler is ready.

        System.out.println("Video Thread running")
    }

    interface VideoStreamingThreadReadyCallback {
        fun onRunning(handler: Handler)
    }
}