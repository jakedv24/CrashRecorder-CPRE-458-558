package com.veatch_tutic.crashrecorder.accelerometer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread
import kotlin.math.pow
import kotlin.math.sqrt

class AccelerometerThread(
    private val messageHandler: Handler,
    private val sensorManager: SensorManager
) : Thread(), SensorEventListener {
    val CRASH_DETECTION_THRESHOLD = 25

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

        val gravity = FloatArray(3)
        val linearAcceleration = DoubleArray(3)

        val alpha = 0.8f

        if (event.values.size < 3) {
            return
        }

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Remove the gravity contribution with the high-pass filter.
        linearAcceleration[0] = (event.values[0] - gravity[0]).toDouble()
        linearAcceleration[1] = (event.values[1] - gravity[1]).toDouble()
        linearAcceleration[2] = (event.values[2] - gravity[2]).toDouble()

        val accelerationForce = sqrt(
            linearAcceleration[0].pow(2.0) + linearAcceleration[1].pow(2.0) + linearAcceleration[2].pow(
                2.0
            )
        )

        if (accelerationForce >= CRASH_DETECTION_THRESHOLD) {
            messageHandler.sendEmptyMessage(VideoStreamingThread.MESSAGE_CODE)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}