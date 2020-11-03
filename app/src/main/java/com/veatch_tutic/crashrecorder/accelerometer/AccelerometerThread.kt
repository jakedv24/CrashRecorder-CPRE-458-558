package com.veatch_tutic.crashrecorder.accelerometer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread
import kotlin.math.abs

class AccelerometerThread(
    private val messageHandler: Handler,
    private val sensorManager: SensorManager
) : Thread(), SensorEventListener {
    val CRASH_DETECTION_THRESHOLD = 5.0

    override fun run() {
        System.out.println("Accelerometer Thread running")

        val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // run and monitor the accelerometer thread
        accelerometer?.let {
            // register this thread as listener
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun destroy() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        System.out.println("sensor changed")

        val gravity = floatArrayOf()
        val linearAcceleration = floatArrayOf()

        val alpha = 0.8f

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Remove the gravity contribution with the high-pass filter.
        linearAcceleration[0] = event.values[0] - gravity[0]
        linearAcceleration[1] = event.values[1] - gravity[1]
        linearAcceleration[2] = event.values[2] - gravity[2]

        val accelerationForce = abs(linearAcceleration[0]) + abs(linearAcceleration[1]) + abs(linearAcceleration[2])

        if (accelerationForce >= CRASH_DETECTION_THRESHOLD) {
            messageHandler.sendEmptyMessage(VideoStreamingThread.MESSAGE_CODE)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}