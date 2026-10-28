package com.muhammadridha.fjalar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.muhammadridha.fjalar.domain.model.AppLocale

val LocalFjalarLocale = LocalAppLocale

enum class FjalarStringKey {
    NAV_BACK,
    PINNED_LOCATION,
    LOCATION_NOT_SET,
    OPERATION_FAILED,

    // App & Identity
    APP_NAME,

    // Top HUD
    HUD_SEARCH_PLACEHOLDER,
    HUD_THEME_SWITCH_LIGHT,
    HUD_THEME_SWITCH_DARK,
    HUD_LOCALE_LABEL,
    HUD_RECENTER_LOCATION,

    // Status Pill
    STATUS_ARMED_SCHEDULED,
    STATUS_ARMED_ONE_TIME,
    STATUS_ARMED_SCHEDULED_UNKNOWN,
    STATUS_ARMED_ONE_TIME_UNKNOWN,
    DISTANCE_UNAVAILABLE,

    // Sheet & Cards
    SHEET_SAVED_TITLE,
    SHEET_ACTIVE_COUNT,
    SHEET_NEW_ALARM,
    CARD_SKIP_TODAY,
    CARD_SKIPPED_TODAY,
    CARD_ONE_TIME_TRIP,
    CARD_LOUD_AND_VIBRATE,

    // Alarm Editor
    EDITOR_TITLE,
    EDITOR_CANCEL,
    EDITOR_SAVE,
    EDITOR_LABEL_PLACEHOLDER,
    EDITOR_START_TIME,
    EDITOR_END_TIME,
    EDITOR_TIME_STEP_MINUS_15,
    EDITOR_TIME_STEP_PLUS_15,
    EDITOR_TRIGGER_RADIUS,
    EDITOR_MODE_ONE_TIME,
    EDITOR_MODE_SCHEDULED,
    EDITOR_ACTIVE_WINDOW,
    EDITOR_ALERT_AND_HAPTICS,
    EDITOR_LOUD_SOUND,
    EDITOR_STRONG_VIBRATE,
    EDITOR_EARPHONE_OVERRIDE,
    EDITOR_DELETE_ALARM,

    // Ringing Wake-up Screen
    RINGING_IN_ZONE_BADGE,
    RINGING_HEADLINE,
    RINGING_SNOOZE_ACTION,
    RINGING_OPERATION_FAILED,
    RINGING_SLIDE_TO_STOP,

    // System Notifications
    NOTIF_MONITORING_TITLE,
    NOTIF_MONITORING_BODY,
    NOTIF_ALARM_APPROACHING,

    // Permissions
    PERM_LOCATION_TITLE,
    PERM_LOCATION_BODY,
    PERM_LOCATION_GRANT,
    PERM_LOCATION_DISMISS,
    PERM_BACKGROUND_SETTINGS_BODY,
    PERM_BACKGROUND_SETTINGS_ACTION,

    // Search Station Modal
    SEARCH_PLACEHOLDER,
    SEARCH_CATEGORY_ALL,
    SEARCH_CATEGORY_TRAIN,
    SEARCH_CATEGORY_METRO,
    SEARCH_CATEGORY_TRANSIT,
    SEARCH_EMPTY_TITLE,
    SEARCH_EMPTY_BODY,
    SEARCH_RECENT_TITLE,
    SEARCH_CLEAR,

    // Bottom Navigation Tabs
    NAV_MAP,
    NAV_ALARMS,
    NAV_ADD,
    NAV_ADD_ALARM,

    // Delete Confirmation Dialog
    DIALOG_DELETE_TITLE,
    DIALOG_DELETE_MESSAGE,
    DIALOG_DELETE_CONFIRM,
    DIALOG_DELETE_CANCEL,

    // Map Pin Picker
    PICKER_TITLE,
    PICKER_CONFIRM,
    PICKER_CHANGE_PIN,
    PICKER_HINT,

    // Empty States
    ALARMS_EMPTY_TITLE,
    ALARMS_EMPTY_BODY,

    // Languages
    LANG_ENGLISH,
    LANG_INDONESIAN,
}

object FjalarStrings {

    private val englishCatalog: Map<FjalarStringKey, String> = mapOf(
        FjalarStringKey.NAV_BACK to "← Back",
        FjalarStringKey.PINNED_LOCATION to "Pinned Location",
        FjalarStringKey.LOCATION_NOT_SET to "📍 Not set",
        FjalarStringKey.OPERATION_FAILED to "Could not save changes. Please try again.",

        FjalarStringKey.APP_NAME to "Wake Me at",

        FjalarStringKey.HUD_SEARCH_PLACEHOLDER to "Search station or destination…",
        FjalarStringKey.HUD_THEME_SWITCH_LIGHT to "Switch to light mode",
        FjalarStringKey.HUD_THEME_SWITCH_DARK to "Switch to dark mode",
        FjalarStringKey.HUD_LOCALE_LABEL to "Language: English (EN)",
        FjalarStringKey.HUD_RECENTER_LOCATION to "Recenter GPS",

        FjalarStringKey.STATUS_ARMED_SCHEDULED to "Armed: %1\$s • %2\$s away • %3\$s–%4\$s",
        FjalarStringKey.STATUS_ARMED_SCHEDULED_UNKNOWN to "Armed: %1\$s • Location unavailable • %2\$s–%3\$s",
        FjalarStringKey.STATUS_ARMED_ONE_TIME_UNKNOWN to "Armed: %1\$s • Location unavailable",
        FjalarStringKey.DISTANCE_UNAVAILABLE to "Distance unavailable",
        FjalarStringKey.STATUS_ARMED_ONE_TIME to "Armed: %1\$s • %2\$s away",

        FjalarStringKey.SHEET_SAVED_TITLE to "Saved & Scheduled Alarms",
        FjalarStringKey.SHEET_ACTIVE_COUNT to "%1\$d Active",
        FjalarStringKey.SHEET_NEW_ALARM to "+ New",
        FjalarStringKey.CARD_SKIP_TODAY to "Skip Today",
        FjalarStringKey.CARD_SKIPPED_TODAY to "Skipped for today",
        FjalarStringKey.CARD_ONE_TIME_TRIP to "One-time trip • %1\$s radius",
        FjalarStringKey.CARD_LOUD_AND_VIBRATE to "Loud + Vibrate",

        FjalarStringKey.EDITOR_TITLE to "Configure Alarm",
        FjalarStringKey.EDITOR_CANCEL to "Cancel",
        FjalarStringKey.EDITOR_SAVE to "Save",
        FjalarStringKey.EDITOR_LABEL_PLACEHOLDER to "e.g. Manggarai Station",
        FjalarStringKey.EDITOR_START_TIME to "Start: %1\$s",
        FjalarStringKey.EDITOR_END_TIME to "End: %1\$s",
        FjalarStringKey.EDITOR_TIME_STEP_MINUS_15 to "-15m",
        FjalarStringKey.EDITOR_TIME_STEP_PLUS_15 to "+15m",
        FjalarStringKey.EDITOR_TRIGGER_RADIUS to "Trigger Radius",
        FjalarStringKey.EDITOR_MODE_ONE_TIME to "One-Time Now",
        FjalarStringKey.EDITOR_MODE_SCHEDULED to "Scheduled Window",
        FjalarStringKey.EDITOR_ACTIVE_WINDOW to "Active Window (%1\$s – %2\$s)",
        FjalarStringKey.EDITOR_ALERT_AND_HAPTICS to "Alert & Haptics",
        FjalarStringKey.EDITOR_LOUD_SOUND to "Loud Sound",
        FjalarStringKey.EDITOR_STRONG_VIBRATE to "Strong Vibrate",
        FjalarStringKey.EDITOR_EARPHONE_OVERRIDE to "Rings in Earphones + Speaker",
        FjalarStringKey.EDITOR_DELETE_ALARM to "Delete Alarm",

        FjalarStringKey.RINGING_IN_ZONE_BADGE to "IN ZONE • %1\$s from target pin",
        FjalarStringKey.RINGING_HEADLINE to "Wake Up! Approaching %1\$s",
        FjalarStringKey.RINGING_OPERATION_FAILED to "Could not stop the alarm. Please try again.",
        FjalarStringKey.RINGING_SNOOZE_ACTION to "Snooze (+2 min)",
        FjalarStringKey.RINGING_SLIDE_TO_STOP to "Slide to Stop Alarm",

        FjalarStringKey.NOTIF_MONITORING_TITLE to "Monitoring commute destination",
        FjalarStringKey.NOTIF_MONITORING_BODY to "Active: %1\$s • %2\$s remaining",
        FjalarStringKey.NOTIF_ALARM_APPROACHING to "Approaching %1\$s",

        FjalarStringKey.PERM_LOCATION_TITLE to "Allow Background Location for Wake-Up Alarms",
        FjalarStringKey.PERM_LOCATION_BODY to "Fjalar checks your GPS distance in the background only while an alarm is armed so it can wake you up even when your screen is locked. Your location never leaves your phone.",
        FjalarStringKey.PERM_LOCATION_GRANT to "Grant Permission",
        FjalarStringKey.PERM_LOCATION_DISMISS to "Dismiss",
        FjalarStringKey.PERM_BACKGROUND_SETTINGS_BODY to "In app settings, open Permissions > Location and select %s. You can skip this step and keep using the map, but scheduled alarms need background location.",
        FjalarStringKey.PERM_BACKGROUND_SETTINGS_ACTION to "Open Settings",

        FjalarStringKey.SEARCH_PLACEHOLDER to "Search stations, stops, or places...",
        FjalarStringKey.SEARCH_CATEGORY_ALL to "All",
        FjalarStringKey.SEARCH_CATEGORY_TRAIN to "Train",
        FjalarStringKey.SEARCH_CATEGORY_METRO to "Metro / MRT",
        FjalarStringKey.SEARCH_CATEGORY_TRANSIT to "Transit",
        FjalarStringKey.SEARCH_EMPTY_TITLE to "No stations found",
        FjalarStringKey.SEARCH_EMPTY_BODY to "Try searching by station name, line, or abbreviation.",
        FjalarStringKey.SEARCH_RECENT_TITLE to "Transit Stations",
        FjalarStringKey.SEARCH_CLEAR to "Clear search",

        FjalarStringKey.NAV_MAP to "Map",
        FjalarStringKey.NAV_ALARMS to "Alarms",
        FjalarStringKey.NAV_ADD to "Add",
        FjalarStringKey.NAV_ADD_ALARM to "Add Alarm",

        FjalarStringKey.DIALOG_DELETE_TITLE to "Delete Alarm?",
        FjalarStringKey.DIALOG_DELETE_MESSAGE to "Are you sure you want to delete '%s'?",
        FjalarStringKey.DIALOG_DELETE_CONFIRM to "Delete",
        FjalarStringKey.DIALOG_DELETE_CANCEL to "Cancel",

        FjalarStringKey.PICKER_TITLE to "Select Destination Pin",
        FjalarStringKey.PICKER_CONFIRM to "Set Location",
        FjalarStringKey.PICKER_CHANGE_PIN to "Change Pin on Map",
        FjalarStringKey.PICKER_HINT to "Pan map to position the crosshair",

        FjalarStringKey.ALARMS_EMPTY_TITLE to "No Alarms Yet",
        FjalarStringKey.ALARMS_EMPTY_BODY to "Tap + Add to set your first wake-up alarm.",

        FjalarStringKey.LANG_ENGLISH to "English (EN)",
        FjalarStringKey.LANG_INDONESIAN to "Bahasa Indonesia (ID)",
    )

    private val indonesianCatalog: Map<FjalarStringKey, String> = mapOf(
        FjalarStringKey.NAV_BACK to "← Kembali",
        FjalarStringKey.PINNED_LOCATION to "Titik Lokasi",
        FjalarStringKey.LOCATION_NOT_SET to "📍 Belum ditentukan",
        FjalarStringKey.OPERATION_FAILED to "Perubahan gagal disimpan. Silakan coba lagi.",

        FjalarStringKey.APP_NAME to "Bangunkan Saya di",

        FjalarStringKey.HUD_SEARCH_PLACEHOLDER to "Cari stasiun atau tujuan…",
        FjalarStringKey.HUD_THEME_SWITCH_LIGHT to "Beralih ke mode terang",
        FjalarStringKey.HUD_THEME_SWITCH_DARK to "Beralih ke mode gelap",
        FjalarStringKey.HUD_LOCALE_LABEL to "Bahasa: Indonesia (ID)",
        FjalarStringKey.HUD_RECENTER_LOCATION to "Pusatkan Ulang GPS",

        FjalarStringKey.STATUS_ARMED_SCHEDULED to "Aktif: %1\$s • %2\$s lagi • %3\$s–%4\$s",
        FjalarStringKey.STATUS_ARMED_SCHEDULED_UNKNOWN to "Aktif: %1\$s • Lokasi tidak tersedia • %2\$s–%3\$s",
        FjalarStringKey.STATUS_ARMED_ONE_TIME_UNKNOWN to "Aktif: %1\$s • Lokasi tidak tersedia",
        FjalarStringKey.DISTANCE_UNAVAILABLE to "Jarak tidak tersedia",
        FjalarStringKey.STATUS_ARMED_ONE_TIME to "Aktif: %1\$s • %2\$s lagi",

        FjalarStringKey.SHEET_SAVED_TITLE to "Alarm Tersimpan & Terjadwal",
        FjalarStringKey.SHEET_ACTIVE_COUNT to "%1\$d Aktif",
        FjalarStringKey.SHEET_NEW_ALARM to "+ Baru",
        FjalarStringKey.CARD_SKIP_TODAY to "Lewati Hari Ini",
        FjalarStringKey.CARD_SKIPPED_TODAY to "Dilewati hari ini",
        FjalarStringKey.CARD_ONE_TIME_TRIP to "Sekali jalan • radius %1\$s",
        FjalarStringKey.CARD_LOUD_AND_VIBRATE to "Suara Keras + Getar",

        FjalarStringKey.EDITOR_TITLE to "Atur Alarm",
        FjalarStringKey.EDITOR_CANCEL to "Batal",
        FjalarStringKey.EDITOR_SAVE to "Simpan",
        FjalarStringKey.EDITOR_LABEL_PLACEHOLDER to "cth. Stasiun Manggarai",
        FjalarStringKey.EDITOR_START_TIME to "Mulai: %1\$s",
        FjalarStringKey.EDITOR_END_TIME to "Selesai: %1\$s",
        FjalarStringKey.EDITOR_TIME_STEP_MINUS_15 to "-15m",
        FjalarStringKey.EDITOR_TIME_STEP_PLUS_15 to "+15m",
        FjalarStringKey.EDITOR_TRIGGER_RADIUS to "Radius Alarm",
        FjalarStringKey.EDITOR_MODE_ONE_TIME to "Sekali Jalan",
        FjalarStringKey.EDITOR_MODE_SCHEDULED to "Jendela Terjadwal",
        FjalarStringKey.EDITOR_ACTIVE_WINDOW to "Waktu Aktif (%1\$s – %2\$s)",
        FjalarStringKey.EDITOR_ALERT_AND_HAPTICS to "Suara & Getaran",
        FjalarStringKey.EDITOR_LOUD_SOUND to "Suara Keras",
        FjalarStringKey.EDITOR_STRONG_VIBRATE to "Getar Kuat",
        FjalarStringKey.EDITOR_EARPHONE_OVERRIDE to "Bunyi di Earphone + Speaker",
        FjalarStringKey.EDITOR_DELETE_ALARM to "Hapus Alarm",

        FjalarStringKey.RINGING_IN_ZONE_BADGE to "DALAM ZONA • %1\$s dari titik tujuan",
        FjalarStringKey.RINGING_HEADLINE to "Bangun! Mendekati %1\$s",
        FjalarStringKey.RINGING_OPERATION_FAILED to "Alarm gagal dihentikan. Silakan coba lagi.",
        FjalarStringKey.RINGING_SNOOZE_ACTION to "Tunda (+2 mnt)",
        FjalarStringKey.RINGING_SLIDE_TO_STOP to "Geser untuk Matikan Alarm",

        FjalarStringKey.NOTIF_MONITORING_TITLE to "Memantau tujuan perjalanan",
        FjalarStringKey.NOTIF_MONITORING_BODY to "Aktif: %1\$s • %2\$s lagi",
        FjalarStringKey.NOTIF_ALARM_APPROACHING to "Mendekati %1\$s",

        FjalarStringKey.PERM_LOCATION_TITLE to "Izinkan Lokasi Latar Belakang untuk Alarm Tujuan",
        FjalarStringKey.PERM_LOCATION_BODY to "Fjalar memeriksa jarak GPS di latar belakang hanya saat alarm aktif agar dapat membangunkan Anda meski layar terkunci. Lokasi Anda tidak pernah dikirim keluar dari ponsel ini.",
        FjalarStringKey.PERM_LOCATION_GRANT to "Izinkan Akses",
        FjalarStringKey.PERM_LOCATION_DISMISS to "Abaikan",
        FjalarStringKey.PERM_BACKGROUND_SETTINGS_BODY to "Di setelan aplikasi, buka Izin > Lokasi lalu pilih %s. Anda dapat melewati langkah ini dan tetap menggunakan peta, tetapi alarm terjadwal memerlukan lokasi latar belakang.",
        FjalarStringKey.PERM_BACKGROUND_SETTINGS_ACTION to "Buka Setelan",

        FjalarStringKey.SEARCH_PLACEHOLDER to "Cari stasiun, halte, atau tempat...",
        FjalarStringKey.SEARCH_CATEGORY_ALL to "Semua",
        FjalarStringKey.SEARCH_CATEGORY_TRAIN to "Kereta",
        FjalarStringKey.SEARCH_CATEGORY_METRO to "Metro / MRT",
        FjalarStringKey.SEARCH_CATEGORY_TRANSIT to "Transit",
        FjalarStringKey.SEARCH_EMPTY_TITLE to "Stasiun tidak ditemukan",
        FjalarStringKey.SEARCH_EMPTY_BODY to "Coba cari berdasarkan nama stasiun, jalur, atau singkatan.",
        FjalarStringKey.SEARCH_RECENT_TITLE to "Stasiun Transit",
        FjalarStringKey.SEARCH_CLEAR to "Hapus pencarian",

        FjalarStringKey.NAV_MAP to "Peta",
        FjalarStringKey.NAV_ALARMS to "Alarm",
        FjalarStringKey.NAV_ADD to "Tambah",
        FjalarStringKey.NAV_ADD_ALARM to "Tambah Alarm",

        FjalarStringKey.DIALOG_DELETE_TITLE to "Hapus Alarm?",
        FjalarStringKey.DIALOG_DELETE_MESSAGE to "Apakah Anda yakin ingin menghapus '%s'?",
        FjalarStringKey.DIALOG_DELETE_CONFIRM to "Hapus",
        FjalarStringKey.DIALOG_DELETE_CANCEL to "Batal",

        FjalarStringKey.PICKER_TITLE to "Pilih Titik Tujuan",
        FjalarStringKey.PICKER_CONFIRM to "Tetapkan Lokasi",
        FjalarStringKey.PICKER_CHANGE_PIN to "Ubah Titik di Peta",
        FjalarStringKey.PICKER_HINT to "Geser peta untuk menempatkan bidik silang",

        FjalarStringKey.ALARMS_EMPTY_TITLE to "Belum Ada Alarm",
        FjalarStringKey.ALARMS_EMPTY_BODY to "Ketuk + Tambah untuk menyetel alarm bangun pertama Anda.",

        FjalarStringKey.LANG_ENGLISH to "English (EN)",
        FjalarStringKey.LANG_INDONESIAN to "Bahasa Indonesia (ID)",
    )

    fun get(key: FjalarStringKey, locale: AppLocale, vararg args: Any): String {
        val catalog = when (locale) {
            AppLocale.EN -> englishCatalog
            AppLocale.ID -> indonesianCatalog
        }
        val template = catalog[key] ?: englishCatalog[key] ?: key.name
        return formatTemplate(template, args)
    }

    private fun formatTemplate(template: String, args: Array<out Any>): String {
        if (args.isEmpty()) return template
        var nextArgument = 0
        // Match only the original template. Inserted user text is never scanned again.
        val placeholders = Regex("%([0-9]+\\$)?[a-zA-Z]")
        return placeholders.replace(template) { match ->
            val position = match.groupValues[1]
            val index = if (position.isEmpty()) nextArgument++ else position.dropLast(1).toInt() - 1
            args.getOrNull(index)?.toString() ?: match.value
        }
    }
}

@Composable
@ReadOnlyComposable
fun fjalarString(key: FjalarStringKey, vararg args: Any): String {
    val locale = LiquidGlassTheme.locale
    return FjalarStrings.get(key, locale, *args)
}
