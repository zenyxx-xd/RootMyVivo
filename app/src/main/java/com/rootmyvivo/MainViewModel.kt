package com.rootmyvivo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootmyvivo.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val deviceInfo: DeviceInfo? = null,
    val supportCheck: SupportCheck? = null,
    val isRunning: Boolean = false,
    val logLines: List<String> = emptyList(),
    val rootStatus: String? = null,
    val selectedKsu: KsuVariant = KsuVariant.RESUKISU,
    val ksuSheetOpen: Boolean = false,
    val installStage: String = "",
)

class MainViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    
    private var executor: ExploitExecutor? = null
    
    init {
        detectDevice()
    }
    
    fun detectDevice() {
        viewModelScope.launch {
            val info = DeviceInfo.detect()
            val check = checkSupport(info)
            _state.value = _state.value.copy(
                deviceInfo = info,
                supportCheck = check,
                rootStatus = if (check.rootAlready) "Рут активен!" else null,
            )
        }
    }
    
    fun startRoot() {
        val info = _state.value.deviceInfo ?: return
        
        executor = ExploitExecutor(info)
        _state.value = _state.value.copy(isRunning = true, logLines = emptyList())
        
        viewModelScope.launch {
            executor!!.execute { progress ->
                when (progress) {
                    is ExploitExecutor.Progress.Stage -> {
                        addLog("[*] ${progress.name}")
                        _state.value = _state.value.copy(installStage = progress.name)
                    }
                    is ExploitExecutor.Progress.Log -> 
                        addLog(progress.line)
                    is ExploitExecutor.Progress.Success -> {
                        addLog("[✓✓✓] ROOT ПОЛУЧЕН! ${progress.context}")
                        _state.value = _state.value.copy(
                            isRunning = false,
                            rootStatus = "Root активен! (${progress.context})"
                        )
                        // Автоматически ставим KSU
                        installKsu()
                    }
                    is ExploitExecutor.Progress.Failure -> {
                        addLog("[✗] ${progress.reason}")
                        if (!progress.recoverable) addLog("Это не исправить повтором.")
                        _state.value = _state.value.copy(isRunning = false)
                    }
                }
            }
        }
    }
    
    fun installKsu() {
        val info = _state.value.deviceInfo ?: return
        val variant = _state.value.selectedKsu
        
        viewModelScope.launch {
            val installer = KsuInstaller(info)
            installer.install(variant) { msg -> addLog(msg) }
        }
    }
    
    fun selectKsu(variant: KsuVariant) {
        _state.value = _state.value.copy(
            selectedKsu = variant, 
            ksuSheetOpen = false
        )
    }
    
    fun toggleKsuSheet() {
        _state.value = _state.value.copy(ksuSheetOpen = !_state.value.ksuSheetOpen)
    }
    
    fun stopExploit() {
        executor?.stop()
        _state.value = _state.value.copy(isRunning = false)
    }
    
    private fun addLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", 
            java.util.Locale.getDefault()).format(java.util.Date())
        _state.value = _state.value.copy(
            logLines = _state.value.logLines + "[$ts] $line"
        )
    }
}
