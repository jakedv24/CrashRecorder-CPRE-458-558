package com.veatch_tutic.crashrecorder.settings

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.*

class SettingsViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {
    companion object {
        val userIDKey = "USER_ID_KEY"
        val videoLengthSettingKey = "VIDEO_LENGTH_SETTING_KEY"
        val defaultVideoLength = 5
    }

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
        videoLengthSetting.value =
            sharedPreferences.getInt(videoLengthSettingKey, defaultVideoLength)
        videoLengthSettingSaved =
            sharedPreferences.getInt(videoLengthSettingKey, defaultVideoLength)
    }

    fun getUserId(): String {
        return if (sharedPreferences.contains(userIDKey)) {
            sharedPreferences.getString(userIDKey, "") ?: ""
        } else {
            val newId = UUID.randomUUID().toString()

            with(sharedPreferences.edit()) {
                putString(userIDKey, newId)
                apply()
            }

            newId
        }
    }
}