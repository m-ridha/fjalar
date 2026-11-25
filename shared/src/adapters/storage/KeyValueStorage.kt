package com.muhammadridha.fjalar.adapters.storage

interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
