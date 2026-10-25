package com.muhammadridha.fjalar.adapters.storage

import platform.Foundation.NSUserDefaults

class UserDefaultsKeyValueStorage(
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStorage {

    override fun getString(key: String): String? = userDefaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        userDefaults.removeObjectForKey(key)
    }
}
