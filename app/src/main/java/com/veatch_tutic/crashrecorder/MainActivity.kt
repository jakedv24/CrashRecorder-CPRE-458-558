package com.veatch_tutic.crashrecorder

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.veatch_tutic.crashrecorder.accelerometer.AccelerometerThread
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread.VideoStreamingThreadReadyCallback

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sensorService = this.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val videoThreadReadyCallback = object : VideoStreamingThreadReadyCallback {
            override fun onRunning(handler: Handler) {
                // when video thread has started and init, start gyro thread and pass in message handler
                val accelerometerThread = AccelerometerThread(handler, sensorService)
                accelerometerThread.start()
            }
        }

        // start video thread
        val videoStreamingThread = VideoStreamingThread(videoThreadReadyCallback)
        videoStreamingThread.start()
    }
}