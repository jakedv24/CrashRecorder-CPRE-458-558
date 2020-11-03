package com.veatch_tutic.crashrecorder.accelerometer

import android.os.Handler
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread

class AccelerometerThread(private val messageHandler: Handler) : Thread() {

    override fun run() {
        System.out.println("Accelerometer Thread running")
        // run and monitor the accelerometer thread

        // on trigger of crash detection, pass message to messageHandler
        messageHandler.sendEmptyMessage(VideoStreamingThread.MESSAGE_CODE)
    }
}