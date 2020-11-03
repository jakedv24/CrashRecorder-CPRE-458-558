package com.veatch_tutic.crashrecorder.gyroscope

import android.os.Handler
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread

class GyroscopeThread(private val messageHandler: Handler) : Thread() {

    override fun run() {
        super.run()

        // run and monitor the gyroscope thread

        // on trigger of crash detection, pass message to messageHandler
        messageHandler.sendEmptyMessage(VideoStreamingThread.MESSAGE_CODE)
    }
}