package com.muhammadridha.fjalar.adapters.storage

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesKeyValueStorage(
    private val preferences: SharedPreferences,
) : KeyValueStorage {

    constructor(
        context: Context,
        name: String = DEFAULT_PREFS_NAME,
    ) : this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    companion object {
        const val DEFAULT_PREFS_NAME = "fjalar_preferences"
    }
}
