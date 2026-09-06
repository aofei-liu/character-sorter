# Android client

The fork's native client for `charsorter.lndyn.com`. Planning, phasing and the
protocol notes live in [`../ROADMAP.md`](../ROADMAP.md) under "The Android
client"; this file is just how to build and run what is here.

## Modules

| Module | Contents | Builds with |
| --- | --- | --- |
| `:client` | API client, models, auth handshake. Pure JVM. | JDK + Gradle alone |
| `:app` | Compose UI. **Not written yet.** | Android SDK |

`:client` must stay free of Android and AndroidX types. That is not a style
preference: it is what makes every risky part of the protocol — the login
handshake, CSRF, the non-uniform error shapes — testable in a cloud session
that has no SDK and cannot download one (`dl.google.com` is off the network
allowlist).

## Consuming `:client` from `:app`

Two constraints the module cannot enforce on its own, both of which bite at
`:app`'s first compile rather than here:

- **OkHttp reaches `:app`'s compile classpath, deliberately.** `OkHttpClient`,
  `HttpUrl` and `CookieJar` all appear in `:client`'s public API — the client
  constructor takes an `OkHttpClient`, and `cookieJar` hands out a
  `SessionCookieJar`, which *is* a `CookieJar`. So okhttp is declared `api`,
  not `implementation`; reverting that makes `client.cookieJar.save()`
  uncompilable for any consumer.
- **`java.time` needs API 26 or desugaring.** `submitComparison` takes an
  `OffsetDateTime`, because the API demands a UTC offset and refuses a future
  timestamp — a weaker type would push that check to the server and cost a
  round trip. On `minSdk` below 26, `:app` must turn on core library
  desugaring:

  ```kotlin
  compileOptions { isCoreLibraryDesugaringEnabled = true }
  dependencies { coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2") }
  ```

## Building and testing

```bash
cd android
./gradlew :client:test        # 35 unit tests against MockWebServer
./gradlew :client:build
```

`LiveSmokeTest` is skipped unless `CHARSORTER_LIVE=1` is set. It makes two
read-only, unauthenticated requests to the deployed site and needs
`*.lndyn.com` on the environment's network allowlist:

```bash
CHARSORTER_LIVE=1 ./gradlew :client:test --tests '*LiveSmokeTest*'
```

## Using the client

```kotlin
val client = CharSorterClient()                  // defaults to the live site
client.login("username", "password")             // 302 + sessionid, or throws
val lists = client.lists()
val next = client.nextComparison(lists[0].id)
val record = client.submitComparison(
    lists[0].id, next.char1!!.id, next.char2!!.id, Verdict.CHAR1_WINS)
client.deleteComparison(lists[0].id, record.id)  // undo
```

Three properties of the API the client cannot paper over, and a caller has to
know about:

- **The pair from `nextComparison` is not re-fetchable.** Glicko samples it
  from a softmax, so asking again returns a different question. Hold the pair
  you were handed and answer it.
- **Undo dies with the process.** The record id comes back only in the `201`
  from this client's own `POST`; the API has no `GET` on `/comparisons`.
- **Do not poll.** Every ranking and every `/next` replays the list's whole
  comparison history server-side.

Persist a session with `client.cookieJar.save()` and `restore()` — the cookies,
never the password. Django's default session lifetime is two weeks, so a
`NotAuthenticatedException` is a normal prompt to log in again.
