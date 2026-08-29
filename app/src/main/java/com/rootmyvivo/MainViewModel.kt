package com.rootmyvivo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootmyvivo.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val deviceInfo: DeviceInfo? = null,
    val supportCheck: SupportCheck? = null,
    val payload: PayloadEntry? = null,
    val isRunning: Boolean = false,
    val logLines: List<String> = emptyList(),
    val rootStatus: RootStatus = RootStatus.Unknown,
    val selectedKsu: KsuVariant = KsuVariant.RESUKISU,
    val ksuSheetOpen: Boolean = false,
    val installStage: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 5,
    val downloadProgress: Float? = null,  // 0..1 или null если не идёт скачивание
    val settings: AppSettings = AppSettings(),
    val transport: ShellBridge.Transport = ShellBridge.Transport.None,
)

enum class RootStatus { Unknown, Checking, Active, NotRooted, Failed }

enum class ThemeMode { AUTO, LIGHT, DARK }

data class AppSettings(
    val language: String = "ru",          // ru / en / zh
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val dynamicColors: Boolean = true,
    val catalogUrl: String = CatalogClient.DEFAULT_URL,
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var engine: ExploitEngine? = null
    private var catalogClient = CatalogClient()

    init {
        _state.value = _state.value.copy(settings = loadSettings())
        catalogClient.catalogUrl = _state.value.settings.catalogUrl
        detectDevice()
        refreshTransport()
    }

    /** Проверить доступный транспорт (adb tcp / Shizuku / нет) */
    fun refreshTransport() {
        viewModelScope.launch {
            val t = ShellBridge.availableTransportSuspending()
            _state.value = _state.value.copy(transport = t)
            if (t == ShellBridge.Transport.Shizuku) {
                ShellBridge.bindShizukuService(RmvApp.instance)
            }
        }
    }

    /** Запросить разрешение Shizuku (показывает системный диалог Shizuku) */
    fun requestShizuku() {
        val requested = ShellBridge.requestShizukuPermission(requestCode = 100) {
            // Пользователь выдал разрешение — обновляем транспорт
            refreshTransport()
        }
        if (!requested) {
            // Уже выдано или binder не готов — просто обновим состояние
            refreshTransport()
        }
    }

    private fun loadSettings(): AppSettings {
        val prefs = RmvApp.instance.getSharedPreferences("rootmyvivo", 0)
        return AppSettings(
            language = prefs.getString("language", "ru") ?: "ru",
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString("theme", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
            }.getOrDefault(ThemeMode.AUTO),
            dynamicColors = prefs.getBoolean("dynamicColors", true),
            catalogUrl = prefs.getString("catalogUrl", CatalogClient.DEFAULT_URL)
                ?.takeIf { it.startsWith("https://") } ?: CatalogClient.DEFAULT_URL,
        )
    }

    fun detectDevice() {
        viewModelScope.launch {
            _state.value = _state.value.copy(rootStatus = RootStatus.Checking)
            val info = DeviceInfo.detect()
            val check = checkSupport(info)

            // Загружаем каталог в фоне и ищем пейлоад
            catalogClient.catalogUrl = _state.value.settings.catalogUrl
            val catalog = catalogClient.fetch().getOrNull()
            val payload = catalog?.let { catalogClient.findPayload(it, info) }

            _state.value = _state.value.copy(
                deviceInfo = info,
                supportCheck = check,
                payload = payload,
                rootStatus = when {
                    check.rootAlready -> RootStatus.Active
                    else -> RootStatus.NotRooted
                },
            )
        }
    }

    fun startRoot() {
        val info = _state.value.deviceInfo ?: return

        engine = ExploitEngine(info, catalogClient)
        _state.value = _state.value.copy(isRunning = true, logLines = emptyList(), rootStatus = RootStatus.Checking)

        viewModelScope.launch {
            engine!!.runFullFlow(_state.value.selectedKsu) { progress ->
                when (progress) {
                    is ExploitEngine.Progress.Stage -> {
                        addLog("[*] ${progress.name}")
                        _state.value = _state.value.copy(
                            installStage = progress.name,
                            currentStep = progress.step,
                            totalSteps = progress.totalSteps,
                        )
                    }
                    is ExploitEngine.Progress.Log ->
                        addLog(progress.line)
                    is ExploitEngine.Progress.Download -> {
                        val fraction = if (progress.totalBytes > 0)
                            progress.bytesRead.toFloat() / progress.totalBytes else 0f
                        _state.value = _state.value.copy(downloadProgress = fraction)
                    }
                    is ExploitEngine.Progress.Success -> {
                        addLog("[✓✓✓] ROOT ПОЛУЧЕН И ЗАКРЕПЛЁН!")
                        _state.value = _state.value.copy(
                            isRunning = false,
                            rootStatus = RootStatus.Active,
                        )
                    }
                    is ExploitEngine.Progress.Failure -> {
                        addLog("[✗] ${progress.reason}")
                        progress.hint?.let { addLog("[i] $it") }
                        _state.value = _state.value.copy(
                            isRunning = false,
                            rootStatus = RootStatus.Failed,
                        )
                    }
                }
            }
        }
    }

    fun selectKsu(variant: KsuVariant) {
        _state.value = _state.value.copy(selectedKsu = variant, ksuSheetOpen = false)
    }

    fun toggleKsuSheet() {
        _state.value = _state.value.copy(ksuSheetOpen = !_state.value.ksuSheetOpen)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val newSettings = transform(_state.value.settings)
        _state.value = _state.value.copy(settings = newSettings)
        catalogClient.catalogUrl = newSettings.catalogUrl
        RmvApp.instance.getSharedPreferences("rootmyvivo", 0).edit()
            .putString("language", newSettings.language)
            .putString("theme", newSettings.themeMode.name)
            .putBoolean("dynamicColors", newSettings.dynamicColors)
            .putString("catalogUrl", newSettings.catalogUrl)
            .apply()
    }

    fun stopExploit() {
        engine?.stop()
        _state.value = _state.value.copy(isRunning = false)
    }

    private fun addLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _state.value = _state.value.copy(
            logLines = _state.value.logLines + "[$ts] $line"
        )
    }
}
