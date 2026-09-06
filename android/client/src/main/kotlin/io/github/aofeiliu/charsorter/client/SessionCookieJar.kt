package io.github.aofeiliu.charsorter.client

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * An in-memory cookie jar for a single origin.
 *
 * Cookies are keyed by name alone, which is only correct because every
 * request this client makes goes to one host. It holds the two that matter:
 * `csrftoken`, read back by [CsrfInterceptor], and `sessionid`.
 *
 * [save] and [restore] exist so a caller can persist the session across
 * process restarts — persist the cookies, never the password.
 */
class SessionCookieJar(private val url: HttpUrl) : CookieJar {

    private val cookies = LinkedHashMap<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                this.cookies.remove(cookie.name)
            } else {
                this.cookies[cookie.name] = cookie
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = live().filter { it.matches(url) }

    /** The current value of a cookie, or null if absent or expired. */
    @Synchronized
    fun value(name: String): String? = live().firstOrNull { it.name == name }?.value

    /** Serialized cookies, each in `Set-Cookie` form, for persisting. */
    @Synchronized
    fun save(): List<String> = live().map { it.toString() }

    /** Replaces the jar's contents with cookies previously returned by [save]. */
    @Synchronized
    fun restore(serialized: List<String>) {
        cookies.clear()
        for (line in serialized) {
            Cookie.parse(url, line)?.let { cookies[it.name] = it }
        }
    }

    @Synchronized
    fun clear() = cookies.clear()

    private fun live(): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeIf { it.value.expiresAt <= now }
        return cookies.values.toList()
    }
}
