package com.veatch_tutic.crashrecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentTransaction
import com.veatch_tutic.crashrecorder.accelerometer.AccelerometerThread
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread
import com.veatch_tutic.crashrecorder.video_streaming.VideoStreamingThread.VideoStreamingThreadReadyCallback
import com.veatch_tutic.crashrecorder.video_streaming.ViewFinderFragment

private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

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

        if (savedInstanceState == null) {
            val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
            ft.replace(R.id.view_finder_placeholder, ViewFinderFragment())
            ft.commit()
        }

        if (!hasPermissions(this)) {
            requestPermissions(
                PERMISSIONS_REQUIRED,
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    PERMISSIONS_REQUIRED,
                    PERMISSIONS_REQUEST_CODE
                )
            }
        }
    }

    companion object {
        const val FLAGS_FULLSCREEN =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        const val ANIMATION_FAST_MILLIS = 50L
        const val ANIMATION_SLOW_MILLIS = 100L
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L

        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}