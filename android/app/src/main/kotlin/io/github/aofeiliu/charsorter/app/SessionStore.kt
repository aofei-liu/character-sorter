package io.github.aofeiliu.charsorter.app

import android.content.Context

/**
 * Persists the client's session cookies across process restarts.
 *
 * Only [io.github.aofeiliu.charsorter.client.SessionCookieJar.save]'s output
 * is stored here — never the password. `SharedPreferences` is not encrypted
 * at rest, which is an acceptable trade-off for a prototype talking to a site
 * whose own README disclaims its security; revisit before anything beyond
 * that scope stores a session this way.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(cookies: List<String>) {
        prefs.edit().putStringSet(KEY_COOKIES, cookies.toSet()).apply()
    }

    fun restore(): List<String> = prefs.getStringSet(KEY_COOKIES, null)?.toList() ?: emptyList()

    fun clear() {
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private companion object {
        const val PREFS_NAME = "charsorter_session"
        const val KEY_COOKIES = "cookies"
    }
}
