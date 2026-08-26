package dev.ziga.iperf3

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    val engine: StateFlow<EngineState> = IperfEngine.state

    private val _config = MutableStateFlow(IperfConfig())
    val config: StateFlow<IperfConfig> = _config.asStateFlow()

    val profiles: StateFlow<List<Profile>> = Settings.profiles(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<RunRecord>> = History.observe(context)

    private var edited = false
    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            val stored = Settings.lastConfig(context).first()
            // Don't clobber anything the user typed while the store was loading.
            if (!edited) _config.value = stored
        }
    }

    fun update(transform: (IperfConfig) -> IperfConfig) {
        edited = true
        _config.update(transform)
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(400)
            Settings.saveLastConfig(context, _config.value)
        }
    }

    fun start() {
        val config = _config.value
        if (config.validate() != null) return
        IperfService.start(context, config)
    }

    fun stop() = IperfService.stop(context)

    fun reset() = IperfEngine.reset()

    fun saveProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { Settings.saveProfile(context, trimmed, _config.value) }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch { Settings.deleteProfile(context, name) }
    }

    fun applyConfig(config: IperfConfig) = update { config }

    fun deleteRun(id: String) {
        viewModelScope.launch { History.delete(context, id) }
    }

    fun clearHistory() {
        viewModelScope.launch { History.clear(context) }
    }
}
