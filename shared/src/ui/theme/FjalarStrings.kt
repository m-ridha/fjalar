package com.muhammadridha.fjalar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.muhammadridha.fjalar.domain.model.AppLocale

val LocalFjalarLocale = LocalAppLocale

typealias FjalarStringKey = com.muhammadridha.fjalar.localization.FjalarStringKey
typealias FjalarStrings = com.muhammadridha.fjalar.localization.FjalarStrings

@Composable
@ReadOnlyComposable
fun fjalarString(key: FjalarStringKey, vararg args: Any): String {
    val locale = LiquidGlassTheme.locale
    return FjalarStrings.get(key, locale, *args)
}

fun compassDirectionKey(bearing: Double): FjalarStringKey =
    com.muhammadridha.fjalar.localization.compassDirectionKey(bearing)

