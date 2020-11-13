package com.veatch_tutic.crashrecorder.settings

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {
    private val videoLengthSettingKey = "VIDEO_LENGTH_SETTING_KEY"
    private val defaultVideoLength = 30

    private val videoLengthSetting = MutableLiveData<Int>()
    private var videoLengthSettingSaved: Int = 0

    init {
        getSettingsFromSharedPreferences()
    }

    fun applyChanges() {
        with(sharedPreferences.edit()) {
            putInt(videoLengthSettingKey, videoLengthSetting.value ?: defaultVideoLength)
            apply()
        }
    }

    fun cancelChanges() {
        videoLengthSetting.value = videoLengthSettingSaved
    }

    fun getVideoLengthSetting(): LiveData<Int> {
        return videoLengthSetting
    }

    fun videoLengthSettingChanged(newVal: Int) {
        videoLengthSetting.value = newVal
    }

    private fun getSettingsFromSharedPreferences() {
        videoLengthSetting.value = sharedPreferences.getInt(videoLengthSettingKey, defaultVideoLength)
        videoLengthSettingSaved = sharedPreferences.getInt(videoLengthSettingKey, defaultVideoLength)
    }
}