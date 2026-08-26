package dev.ziga.iperf3

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Past runs, newest first, persisted as a single JSON file in the app's data dir. */
object History {

    private const val FILE_NAME = "history.json"
    private const val MAX_ENTRIES = 200

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _records = MutableStateFlow<List<RunRecord>>(emptyList())
    private var loaded = false

    fun observe(context: Context): StateFlow<List<RunRecord>> {
        val app = context.applicationContext
        scope.launch { mutex.withLock { ensureLoaded(app) } }
        return _records.asStateFlow()
    }

    suspend fun append(context: Context, record: RunRecord) = edit(context) { records ->
        (listOf(record) + records).take(MAX_ENTRIES)
    }

    suspend fun delete(context: Context, id: String) = edit(context) { records ->
        records.filterNot { it.id == id }
    }

    suspend fun clear(context: Context) = edit(context) { emptyList() }

    /** Serialises every mutation and keeps the file I/O off the caller's thread. */
    private suspend fun edit(context: Context, transform: (List<RunRecord>) -> List<RunRecord>) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureLoaded(app)
                write(app, transform(_records.value))
            }
        }
    }

    private fun ensureLoaded(app: Context) {
        if (loaded) return
        loaded = true
        val file = file(app)
        if (!file.exists()) return
        _records.value = runCatching {
            json.decodeFromString<List<RunRecord>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun write(app: Context, records: List<RunRecord>) {
        _records.value = records
        runCatching { file(app).writeText(json.encodeToString(records)) }
    }

    private fun file(app: Context) = File(app.filesDir, FILE_NAME)
}
