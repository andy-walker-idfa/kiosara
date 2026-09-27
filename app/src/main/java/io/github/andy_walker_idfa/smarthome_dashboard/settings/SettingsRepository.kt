package io.github.andy_walker_idfa.smarthome_dashboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Read/write access to app settings. Implementations must be safe to call from any thread. */
interface SettingsRepository {
    /** Emits the current settings immediately and then every change. */
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Settings>) : SettingsRepository {
    override val settings: Flow<Settings> =
        dataStore.data.catch { e ->
            if (e is IOException) {
                AppLog.e(TAG, "Reading settings failed; using defaults", e)
                emit(SettingsSerializer.defaultValue)
            } else {
                throw e
            }
        }

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.updateData(transform)
    }

    companion object {
        private const val TAG = "Settings"
        const val FILE_NAME = "settings.json"

        fun create(context: Context, scope: CoroutineScope): DataStoreSettingsRepository = DataStoreSettingsRepository(
            DataStoreFactory.create(
                serializer = SettingsSerializer,
                corruptionHandler =
                    ReplaceFileCorruptionHandler { e ->
                        AppLog.e(TAG, "Settings file corrupt; resetting to defaults", e)
                        SettingsSerializer.defaultValue
                    },
                scope = scope,
                produceFile = { File(context.filesDir, "datastore/$FILE_NAME") }
            )
        )
    }
}
