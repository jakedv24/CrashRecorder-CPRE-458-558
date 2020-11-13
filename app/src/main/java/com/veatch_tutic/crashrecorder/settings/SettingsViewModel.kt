package com.veatch_tutic.crashrecorder.settings

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {
    private val videoLengthSettingKey = "VIDEO_LENGTH_SETTING_KEY"

    private val videoLengthSetting = MutableLiveData<Int>()

    init {
        getSettingsFromSharedPreferences()
    }

    fun getVideoLengthSetting(): LiveData<Int> {
        return videoLengthSetting
    }

    fun videoLengthSettingChanged(newVal: Int) {
        videoLengthSetting.value = newVal

        with(sharedPreferences.edit()) {
            putInt(videoLengthSettingKey, newVal)
            apply()
        }
    }

    private fun getSettingsFromSharedPreferences() {
        videoLengthSetting.value = sharedPreferences.getInt(videoLengthSettingKey, 30)
    }
}