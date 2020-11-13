package com.veatch_tutic.crashrecorder.settings

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.service.autofill.TextValueSanitizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.Observer
import com.veatch_tutic.crashrecorder.R
import com.veatch_tutic.crashrecorder.utils.FullScreenBottomSheetDialogFragment

class SettingsFragment : FullScreenBottomSheetDialogFragment() {
    private lateinit var videoLengthLabel: TextView
    private lateinit var videoLengthControl: SeekBar

    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsViewModel = SettingsViewModel(requireActivity().getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        videoLengthControl = view.findViewById(R.id.video_length_control)
        videoLengthLabel = view.findViewById(R.id.video_length_label)

        settingsViewModel.getVideoLengthSetting().observe(this.viewLifecycleOwner, Observer {
            videoLengthControl.progress = it
            videoLengthLabel.text = getString(R.string.saved_video_length, it)
        })

        val seekBarChangeListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                settingsViewModel.videoLengthSettingChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // no-op
            }

        }

        videoLengthControl.setOnSeekBarChangeListener(seekBarChangeListener)

        return view
    }
}