package io.github.andy_walker_idfa.smarthome_dashboard.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** DataStore serializer storing [Settings] as JSON. Unknown keys are ignored so downgrades don't lose everything. */
object SettingsSerializer : Serializer<Settings> {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // Unknown enum values (e.g. from a newer version) fall back to the field default.
            coerceInputValues = true
        }

    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings = try {
        migrate(json.decodeFromString(Settings.serializer(), input.readBytes().decodeToString()))
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot decode settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Cannot decode settings", e)
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(json.encodeToString(Settings.serializer(), t).encodeToByteArray())
    }

    /** Upgrades settings written by an older schema. Version 1 is the first schema, so there is nothing to do yet. */
    fun migrate(settings: Settings): Settings = settings.copy(schemaVersion = Settings.CURRENT_SCHEMA_VERSION)
}
