package com.muhammadridha.fjalar.domain.ports.outbound

import com.muhammadridha.fjalar.domain.model.AppLocale
import com.muhammadridha.fjalar.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesPort {
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    fun observeLocale(): Flow<AppLocale>
    suspend fun setLocale(locale: AppLocale)
}
