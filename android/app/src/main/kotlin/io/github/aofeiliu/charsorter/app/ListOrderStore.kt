package io.github.aofeiliu.charsorter.app

import android.content.Context

/**
 * Persists the caller's chosen list order across process restarts.
 *
 * A delimited string, not the `putStringSet` [SessionStore] uses for cookies:
 * a set has no order, which is the only thing being stored here. The order is
 * per-device by design — the server has nowhere to keep it.
 */
class ListOrderStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(ids: List<Int>) {
        prefs.edit().putString(KEY_ORDER, ids.joinToString(SEPARATOR)).apply()
    }

    fun restore(): List<Int> {
        val stored = prefs.getString(KEY_ORDER, null) ?: return emptyList()
        return stored.split(SEPARATOR).mapNotNull { it.toIntOrNull() }
    }

    private companion object {
        const val PREFS_NAME = "charsorter_list_order"
        const val KEY_ORDER = "order"
        const val SEPARATOR = ","
    }
}
