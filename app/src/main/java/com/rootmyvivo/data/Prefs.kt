package com.rootmyvivo.data

import android.content.Context

enum class ThemeMode { AUTO, LIGHT, DARK }

/** Тема оформления: стиль поведения, цвета не меняет. */
enum class AppThemeName(val id: String) {
    MONET("monet"),
    /** Заготовка — в списке тем показана, но некликабельна. */
    ORIGIN_OS("originos");

    companion object {
        fun byId(id: String?): AppThemeName =
            entries.firstOrNull { it.id == id } ?: MONET
    }
}

data class Settings(
    val language: String = "",     // "" = системный, ru / en / zh
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val appTheme: AppThemeName = AppThemeName.MONET,
    val dynamicColors: Boolean = true,
    val catalogUrl: String = Catalog.DEFAULT_URL,
    val warnDismissed: Boolean = false,
    val softRebootConfirmDismissed: Boolean = false,
    val restartConfirmDismissed: Boolean = false,
    val bootRestore: Boolean = true,
    /** Автопоиск обновлений приложения после каждого запуска */
    val autoUpdateCheck: Boolean = true,
)

/** Хранилище настроек и флагов (SharedPreferences — sync-чтение на старте). */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("neo_prefs", Context.MODE_PRIVATE)

    fun settings(): Settings = Settings(
        language = sp.getString(KEY_LANGUAGE, "") ?: "",
        themeMode = runCatching {
            ThemeMode.valueOf(sp.getString(KEY_THEME, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
        }.getOrDefault(ThemeMode.AUTO),
        dynamicColors = sp.getBoolean(KEY_DYNAMIC, true),
        appTheme = AppThemeName.byId(sp.getString(KEY_APP_THEME, null)),
        catalogUrl = sp.getString(KEY_CATALOG, null)
            ?.takeIf { it.startsWith("https://") } ?: Catalog.DEFAULT_URL,
        warnDismissed = sp.getBoolean(KEY_WARN_DISMISSED, false),
        softRebootConfirmDismissed = sp.getBoolean(KEY_SR_CONFIRM, false),
        restartConfirmDismissed = sp.getBoolean(KEY_RESTART_CONFIRM, false),
        bootRestore = sp.getBoolean(KEY_BOOT_RESTORE, true),
        autoUpdateCheck = sp.getBoolean(KEY_AUTO_UPDATE, true),
    )

    fun saveSettings(s: Settings) {
        sp.edit()
            .putString(KEY_LANGUAGE, s.language)
            .putString(KEY_THEME, s.themeMode.name)
            .putBoolean(KEY_DYNAMIC, s.dynamicColors)
            .putString(KEY_APP_THEME, s.appTheme.id)
            .putString(KEY_CATALOG, s.catalogUrl)
            .putBoolean(KEY_WARN_DISMISSED, s.warnDismissed)
            .putBoolean(KEY_SR_CONFIRM, s.softRebootConfirmDismissed)
            .putBoolean(KEY_RESTART_CONFIRM, s.restartConfirmDismissed)
            .putBoolean(KEY_BOOT_RESTORE, s.bootRestore)
            .putBoolean(KEY_AUTO_UPDATE, s.autoUpdateCheck)
            .apply()
    }

    /** Первый успешный root зафиксирован — ADB-транспорт разрешён. */
    var firstRootDone: Boolean
        get() = sp.getBoolean(KEY_ROOT_DONE, false)
        set(value) {
            sp.edit().putBoolean(KEY_ROOT_DONE, value).apply()
        }

    /**
     * Root получен, но soft reboot ещё не выполнялся — показать кнопку на главной.
     * Привязано к boot_id: после полной перезагрузки устройства ядро чистое —
     * статус сбрасывается автоматически.
     */
    var softRebootPending: Boolean
        get() = sp.getBoolean(KEY_SR_PENDING, false)
        set(value) {
            sp.edit().putBoolean(KEY_SR_PENDING, value).apply()
        }

    /** boot_id сессии, в которой был получен root. */
    var rootBootId: String
        get() = sp.getString(KEY_ROOT_BOOT_ID, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_ROOT_BOOT_ID, value).apply()
        }

    /** Текущий boot_id устройства (пустая строка при ошибке чтения). */
    fun currentBootId(): String = try {
        java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
    } catch (_: Exception) {
        ""
    }

    /** Статус «нужен soft reboot» с учётом перезагрузок устройства. */
    fun softRebootPendingActual(): Boolean {
        if (!softRebootPending) return false
        val boot = currentBootId()
        // Перезагрузка устройства = новое ядро, soft reboot не нужен
        if (boot.isNotEmpty() && rootBootId.isNotEmpty() && boot != rootBootId) {
            softRebootPending = false
            return false
        }
        return true
    }

    /** Выбранный рут-менеджер (id из KsuVariant). */
    var selectedKsu: String
        get() = sp.getString(KEY_KSU, "resukisu") ?: "resukisu"
        set(value) {
            sp.edit().putString(KEY_KSU, value).apply()
        }

    /** Авто-восстановление рута после перезагрузки (перезапуск эксплойта). */
    var bootRestoreEnabled: Boolean
        get() = sp.getBoolean(KEY_BOOT_RESTORE, true)
        set(value) {
            sp.edit().putBoolean(KEY_BOOT_RESTORE, value).apply()
        }

    /**
     * Фактический пакет установленного менеджера. У spoofed-сборок он
     * рандомный (vctsrt.cntgtj.uqfwgg…) — храним то, что вытащили из APK.
     */
    var managerPackage: String
        get() = sp.getString(KEY_MANAGER_PKG, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_MANAGER_PKG, value).apply()
        }

    /** Последняя попытка boot-восстановления (wall clock, мс) — гард от бутлупа. */
    var bootRestoreLastAttempt: Long
        get() = sp.getLong(KEY_BOOT_RESTORE_AT, 0L)
        set(value) {
            sp.edit().putLong(KEY_BOOT_RESTORE_AT, value).apply()
        }

    /** Модуль какого варианта загружен в ядре сейчас (id KsuVariant). */
    var loadedModuleVariant: String
        get() = sp.getString(KEY_LOADED_VARIANT, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_LOADED_VARIANT, value).apply()
        }

    /** Меню разработчика разблокировано (7 тапов по версии в «О приложении»). */
    var devUnlocked: Boolean
        get() = sp.getBoolean(KEY_DEV_UNLOCKED, false)
        set(value) {
            sp.edit().putBoolean(KEY_DEV_UNLOCKED, value).apply()
        }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamicColors"
        const val KEY_APP_THEME = "appTheme"
        const val KEY_CATALOG = "catalogUrl"
        const val KEY_WARN_DISMISSED = "warnDismissed"
        const val KEY_SR_CONFIRM = "softRebootConfirmDismissed"
        const val KEY_RESTART_CONFIRM = "restartConfirmDismissed"
        const val KEY_ROOT_DONE = "firstRootDone"
        const val KEY_SR_PENDING = "softRebootPending"
        const val KEY_ROOT_BOOT_ID = "rootBootId"
        const val KEY_KSU = "selectedKsu"
        const val KEY_BOOT_RESTORE = "bootRestoreEnabled"
        const val KEY_MANAGER_PKG = "managerPackage"
        const val KEY_BOOT_RESTORE_AT = "bootRestoreLastAttempt"
        const val KEY_LOADED_VARIANT = "loadedModuleVariant"
        const val KEY_DEV_UNLOCKED = "devUnlocked"
        const val KEY_AUTO_UPDATE = "autoUpdateCheck"
    }
}
