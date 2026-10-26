package com.muhammadridha.fjalar.adapters.scheduler

import com.muhammadridha.fjalar.resources.Res
import com.muhammadridha.fjalar.resources.app_name
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSBundle

/**
 * Localization boundary for iOS background notification strings.
 * Isolates language resolution and resource catalog mapping from the scheduler adapter.
 */
interface IosNotificationTitleResolver {
    suspend fun resolveTitle(preferredLanguage: String? = null): String
}

open class DefaultIosNotificationTitleResolver(
    private val appNameResourceProvider: (suspend () -> String) = { getString(Res.string.app_name) },
    private val localizedBundleResolver: (languageCode: String?) -> String? = ::resolveLocalizedBundleTitle,
) : IosNotificationTitleResolver {

    override suspend fun resolveTitle(preferredLanguage: String?): String {
        if (preferredLanguage != null) {
            val primarySubtag = preferredLanguage.lowercase().split('-', '_').firstOrNull() ?: ""
            // First attempt to resolve dynamically from localized bundle strings (e.g. InfoPlist.strings)
            val bundleTitle = localizedBundleResolver(primarySubtag)
            if (!bundleTitle.isNullOrBlank()) {
                return bundleTitle
            }

            return if (primarySubtag == "id" || primarySubtag == "in") {
                INDONESIAN_APP_NAME
            } else {
                ENGLISH_APP_NAME
            }
        }

        return try {
            appNameResourceProvider()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String)
                ?: (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleName") as? String)
                ?: ENGLISH_APP_NAME
        }
    }

    companion object {
        const val ENGLISH_APP_NAME = "Wake Me at"
        const val INDONESIAN_APP_NAME = "Bangunkan Saya di"

        fun resolveLocalizedBundleTitle(languageCode: String?): String? {
            if (languageCode.isNullOrBlank()) return null
            val path = NSBundle.mainBundle.pathForResource(name = languageCode, ofType = "lproj")
            val bundle = if (path != null) NSBundle.bundleWithPath(path) else null
            val localized = bundle?.localizedStringForKey(key = "CFBundleDisplayName", value = null, table = "InfoPlist")
                ?: bundle?.localizedStringForKey(key = "CFBundleName", value = null, table = "InfoPlist")
            return localized?.takeIf { it != "CFBundleDisplayName" && it != "CFBundleName" && it.isNotBlank() }
        }
    }
}
