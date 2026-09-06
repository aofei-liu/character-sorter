package io.github.aofeiliu.charsorter.client

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds what Django needs on a write.
 *
 * The API views are not `csrf_exempt` — they authenticate from the session,
 * so `CsrfViewMiddleware` is what stops a cross-site write. Two headers are
 * required on every unsafe method: `X-CSRFToken` from the cookie jar, and a
 * `Referer` of the site's own origin, because Django checks the referer
 * strictly over HTTPS.
 */
internal class CsrfInterceptor(
    private val origin: String,
    private val jar: SessionCookieJar
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method == "GET") {
            return chain.proceed(request)
        }
        val builder = request.newBuilder().header("Referer", origin)
        jar.value(CSRF_COOKIE)?.let { builder.header("X-CSRFToken", it) }
        return chain.proceed(builder.build())
    }
}
