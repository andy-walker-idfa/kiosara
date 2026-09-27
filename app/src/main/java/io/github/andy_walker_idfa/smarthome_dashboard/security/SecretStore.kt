package io.github.andy_walker_idfa.smarthome_dashboard.security

/**
 * Small encrypted key/value store for credentials (MQTT password, API token, PIN hash).
 *
 * [get] returns null both when a secret was never stored and when it can no longer be decrypted
 * (e.g. the Keystore key was lost). Callers must treat null as "missing" and ask the user to
 * re-enter the value.
 */
interface SecretStore {
    fun get(key: String): String?

    /** Returns false if the value could not be stored (the failure is logged). */
    fun put(key: String, value: String): Boolean

    fun remove(key: String)
}
