package com.muhammadridha.fjalar.adapters.localization

import android.content.Context
import android.content.res.Configuration
import com.muhammadridha.fjalar.domain.model.AppLocale
import java.util.Locale

/** Resolve app-owned notification resources without changing the system language. */
fun Context.withAppLocale(locale: AppLocale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(locale.tag))
    return createConfigurationContext(configuration)
}
