package com.veatch_tutic.crashrecorder.video_streaming

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.veatch_tutic.crashrecorder.BuildConfig
import com.veatch_tutic.crashrecorder.MainActivity
import com.veatch_tutic.crashrecorder.R
import com.veatch_tutic.crashrecorder.settings.SettingsFragment
import com.veatch_tutic.crashrecorder.utils.AutoFitSurfaceView
import com.veatch_tutic.crashrecorder.utils.OrientationLiveData
import com.veatch_tutic.crashrecorder.utils.getPreviewOutputSize
import kotlinx.coroutines.*
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ViewFinderFragment : Fragment() {
    private lateinit var cameraId: String
    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    /** File where the recording will be saved */
    private val outputFile: File
        get() {
            return createFile(requireContext(), "mp4")
        }

    private lateinit var currentOutputFile: File

    /**
     * Setup a persistent [Surface] for the recorder so we can use it as an output target for the
     * camera session without preparing the recorder
     */
    private val recorderSurface: Surface by lazy {

        // Get a persistent Surface from MediaCodec, don't forget to release when done
        val surface = MediaCodec.createPersistentInputSurface()

        // Prepare and release a dummy MediaRecorder with our new surface
        // Required to allocate an appropriately sized buffer before passing the Surface as the
        //  output target to the capture session
        createRecorder(surface).apply {
            prepare()
            release()
        }

        surface
    }

    /** Saves the video recording */
    private lateinit var recorder: MediaRecorder

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Where the camera preview is displayed */
    private lateinit var viewFinder: AutoFitSurfaceView

    /** Fab that controls starting recording */
    private lateinit var startRecording: FloatingActionButton

    /** Fab that controls stopping recording*/
    private lateinit var stopRecord: FloatingActionButton

    /** Fab controlling opening of the settings */
    private lateinit var settingsFAB: FloatingActionButton

    /** Captures frames from a [CameraDevice] for our video recording */
    private lateinit var session: CameraCaptureSession

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    private lateinit var dateTextView: TextView

    /** Requests used for preview only in the [CameraCaptureSession] */
    private val previewRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            // Add the preview surface target
            addTarget(viewFinder.holder.surface)
        }.build()
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(viewFinder.holder.surface)
            addTarget(recorderSurface)
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
        }.build()
    }

    private var recordingStartMillis: Long = 0L

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.view_finder, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraId = context?.let { getCameraId(it, CameraCharacteristics.LENS_FACING_BACK) }!!
    }

    private fun getCameraId(context: Context, facing: Int): String {
        val manager = context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager

        return manager.cameraIdList.first {
            manager
                .getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = view.findViewById(R.id.view_finder)
        startRecording = view.findViewById(R.id.start_recording)
        stopRecord = view.findViewById(R.id.stop_recording)
        dateTextView = view.findViewById(R.id.date_view)
        settingsFAB = view.findViewById(R.id.settings_button)

        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                    viewFinder.display, characteristics, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                viewFinder.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                viewFinder.post { initializeCamera() }
            }
        })

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                    orientation -> Log.d(TAG, "Orientation changed: $orientation")
            })
        }

        settingsFAB.setOnClickListener {
            val fragmentTransaction = requireFragmentManager().beginTransaction()
            SettingsFragment().show(fragmentTransaction, null)
        }
    }

    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    private fun createRecorder(surface: Surface) = MediaRecorder().apply {
        currentOutputFile = outputFile
        val previewSize = getPreviewOutputSize(
            viewFinder.display, characteristics, SurfaceHolder::class.java)

        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(currentOutputFile.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        setVideoFrameRate(60)
        setVideoSize(previewSize.width, previewSize.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setInputSurface(surface)
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating request
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        recorder = createRecorder(recorderSurface)
        // Open the selected camera
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(viewFinder.holder.surface, recorderSurface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        // Sends the capture request as frequently as possible until the session is torn down or
        //  session.stopRepeating() is called
        session.setRepeatingRequest(previewRequest, null, cameraHandler)

        // React to user touching the capture button
        startRecording.setOnClickListener {
            startRecording.visibility = View.GONE
            stopRecord.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {

                // Prevents screen rotation during the video recording
                requireActivity().requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_LOCKED

                // Start recording repeating requests, which will stop the ongoing preview
                //  repeating requests without having to explicitly call `session.stopRepeating`
                session.setRepeatingRequest(recordRequest, null, cameraHandler)

                // Finalizes recorder setup and starts recording
                recorder.apply {
                    // Sets output orientation based on current sensor value at start time
                    relativeOrientation.value?.let { it1 -> setOrientationHint(it1) }
                    prepare()
                    start()
                }
                recordingStartMillis = System.currentTimeMillis()
                Log.d(TAG, "Recording started")

                //TODO: Starts recording animation
                //overlay.post(animationTask)
            }
        }

        stopRecord.setOnClickListener {
            startRecording.visibility = View.VISIBLE
            stopRecord.visibility = View.GONE
            lifecycleScope.launch(Dispatchers.IO) {
                // Unlocks screen rotation after recording finished
                requireActivity().requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
                val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
                if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                    delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
                }

                Log.d(TAG, "Recording stopped. Output file: $currentOutputFile")
                recorder.stop()

                // Removes recording animation
                //TODO: Handle stop recording
                //overlay.removeCallbacks(animationTask)

                // Broadcasts the media file to the rest of the system
                MediaScannerConnection.scanFile(
                    view?.context, arrayOf(currentOutputFile.absolutePath), null, null)

                // Launch external activity via intent to play video recorded using our provider
                startActivity(Intent().apply {
                    action = Intent.ACTION_VIEW
                    type = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(currentOutputFile.extension)
                    val authority = "${BuildConfig.APPLICATION_ID}.provider"
                    data = view?.context?.let { it1 -> FileProvider.getUriForFile(it1, authority, currentOutputFile) }
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                })

                // Finishes our current camera screen
                delay(MainActivity.ANIMATION_SLOW_MILLIS)
            }
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when(error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Creates a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine)
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Creates a capture session using the predefined targets, and defines a session state
        // callback which resumes the coroutine once the session is configured
        device.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        recorder.release()
        recorderSurface.release()
    }

    companion object {
        private val TAG = ViewFinderFragment::class.java.simpleName

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }
    }
}