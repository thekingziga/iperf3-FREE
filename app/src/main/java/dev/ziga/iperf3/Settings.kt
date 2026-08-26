package dev.ziga.iperf3

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iperf3")

/** Persists the form the user last used plus their saved profiles. */
object Settings {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val LAST_CONFIG = stringPreferencesKey("last_config")
    private val PROFILES = stringPreferencesKey("profiles")

    fun lastConfig(context: Context): Flow<IperfConfig> =
        context.dataStore.data.map { prefs ->
            prefs[LAST_CONFIG]?.let { stored ->
                runCatching { json.decodeFromString<IperfConfig>(stored) }.getOrNull()
            } ?: IperfConfig()
        }

    suspend fun saveLastConfig(context: Context, config: IperfConfig) {
        context.dataStore.edit { it[LAST_CONFIG] = json.encodeToString(config) }
    }

    fun profiles(context: Context): Flow<List<Profile>> =
        context.dataStore.data.map { prefs -> decodeProfiles(prefs[PROFILES]) }

    suspend fun saveProfile(context: Context, name: String, config: IperfConfig) {
        context.dataStore.edit { prefs ->
            val existing = decodeProfiles(prefs[PROFILES]).filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[PROFILES] = json.encodeToString(existing + Profile(name, config))
        }
    }

    suspend fun deleteProfile(context: Context, name: String) {
        context.dataStore.edit { prefs ->
            prefs[PROFILES] = json.encodeToString(decodeProfiles(prefs[PROFILES]).filterNot { it.name == name })
        }
    }

    private fun decodeProfiles(stored: String?): List<Profile> = stored
        ?.let { runCatching { json.decodeFromString<List<Profile>>(it) }.getOrNull() }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()
}
