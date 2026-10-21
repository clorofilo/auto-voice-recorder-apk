package com.example.auto_voice_recorder.security

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] for JVM unit tests. Pure interface implementation,
 * no Android framework or Robolectric required.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data: MutableMap<String, Any?> = mutableMapOf()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        if (data.containsKey(key)) data[key] as? String else defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (data.containsKey(key)) data[key] as? MutableSet<String> else defValues

    override fun getInt(key: String?, defValue: Int): Int =
        if (data.containsKey(key)) data[key] as? Int ?: defValue else defValue

    override fun getLong(key: String?, defValue: Long): Long =
        if (data.containsKey(key)) data[key] as? Long ?: defValue else defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        if (data.containsKey(key)) data[key] as? Float ?: defValue else defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        if (data.containsKey(key)) data[key] as? Boolean ?: defValue else defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listeners.remove(listener)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toMutableSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                pending.remove(key)
                removals.add(key)
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            pending.clear()
            removals.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
            clearAll = false
            pending.clear()
            removals.clear()
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) {
                removals.remove(key)
                pending[key] = value
            }
            return this
        }
    }
}
