package me.lampu.lampcord.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.lampu.lampcord.shared.utils.getCurrentTimeMillis
import me.lampu.lampcord.shared.utils.getDeviceName
import me.lampu.lampcord.shared.utils.getOsVersion
import me.lampu.lampcord.shared.utils.getPlatformName
import me.lampu.lampcord.shared.utils.randomUUID
import me.lampu.lampcord.shared.utils.Logging as SharedLogging
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Shared HTTP foundation for every Discord API client. Owns the token, Discord identity/session markers (used in X-Super-Properties), and the request-header builders.
class RestClient(
    val httpClient: HttpClient,
    val json: Json
) {
    var token: String? = null
        private set

    fun closeClient() {
        httpClient.close()
    }

    val unauthorizedEvents = MutableSharedFlow<Unit>()

    init {
        httpClient.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
            // Only a 401 from Discord's own API, on a request that actually carried our token,
            // means the session is dead. Third-party hosts (usrbg, UserPFP, badges, Catbox) share
            // this client and must never be able to log the user out.
            if (response.status == HttpStatusCode.Unauthorized && token != null) {
                val request = response.call.request
                val isDiscordApi = request.url.host in DISCORD_API_HOSTS &&
                    request.url.encodedPath.startsWith("/api/")
                val sentToken = request.headers[HttpHeaders.Authorization]
                if (isDiscordApi && sentToken != null && sentToken == token) {
                    unauthorizedEvents.emit(Unit)
                }
            }
            proceedWith(response)
        }
    }

    private companion object {
        val DISCORD_API_HOSTS = setOf("discord.com", "ptb.discord.com", "canary.discord.com", "discordapp.com")
    }

    private val apiVersion = 9
    val apiBase = "https://discord.com/api/v$apiVersion"

    val vendorId = randomUUID()
    val launchId = randomUUID()
    val heartbeatSessionId = randomUUID()
    val launchSignature = (getCurrentTimeMillis() * 1_000_000L).toString()

    fun setToken(token: String?) {
        this.token = token
    }

    /** Everything in it is fixed for the process, so build it once instead of on every request. */
    private val cachedSuperProperties: String by lazy { buildSuperProperties() }

    fun getSuperProperties(): String = cachedSuperProperties

    @OptIn(ExperimentalEncodingApi::class)
    private fun buildSuperProperties(): String {
        val platform = getPlatformName()
        val isMobile = platform == "android" || platform == "ios"

        val properties = buildJsonObject {
            put("os", if (platform == "macos") "Mac OS X" else platform.replaceFirstChar { it.uppercase() })
            put("browser", if (isMobile) "Discord Android" else "Discord Client")
            put("device", getDeviceName())
            put("system_locale", "en-US")
            put("has_client_mods", false)
            put("client_version", if (isMobile) "341.0 - rn" else DESKTOP_CLIENT_VERSION)
            put("release_channel", "stable")
            put("device_vendor_id", vendorId)
            put("design_id", 2)
            put("browser_user_agent", if (isMobile) {
                if (platform == "android") "Discord-Android/341200;RNA" else "Discord/105180 CFNetwork/1410.0.3 Darwin/22.4.0"
            } else "")
            put("browser_version", if (isMobile) "" else "37.6.0")
            put("os_version", getOsVersion())
            put("client_build_number", if (isMobile) 6081 else DESKTOP_CLIENT_BUILD)
            put("client_event_source", JsonNull)
            if (isMobile) {
                put("client_launch_id", launchId)
                put("launch_signature", launchSignature)
                put("client_app_state", "active")
                put("client_heartbeat_session_id", heartbeatSessionId)
            } else {
                put("client_launch_id", JsonNull)
                put("launch_signature", "")
                put("client_app_state", JsonNull)
                put("client_heartbeat_session_id", JsonNull)
            }
        }
        val jsonString = json.encodeToString(properties)
        return Base64.encode(jsonString.encodeToByteArray())
    }
}

fun HttpRequestBuilder.standardHeaders(rest: RestClient) {
    val platform = getPlatformName()
    val isMobile = platform == "android" || platform == "ios"

    val userAgent = if (isMobile) {
        if (platform == "android") {
            "Discord-Android/341200;RNA"
        } else {
            "Discord/105180 CFNetwork/1410.0.3 Darwin/22.4.0"
        }
    } else {
        desktopUserAgent()
    }

    header("User-Agent", userAgent)
    header("Accept-Language", "en-US,en;q=0.9")
    header("X-Super-Properties", rest.getSuperProperties())
    header("X-Discord-Locale", "en-US")
    rest.token?.let { header(HttpHeaders.Authorization, it) }
}

fun HttpRequestBuilder.loginHeaders(rest: RestClient, fingerprint: String? = null) {
    val platform = getPlatformName()
    val isMobile = platform == "android" || platform == "ios"
    val userAgent = if (isMobile) {
        if (platform == "android") "Discord-Android/341200;RNA" else "Discord/105180 CFNetwork/1410.0.3 Darwin/22.4.0"
    } else {
        desktopUserAgent()
    }

    header("User-Agent", userAgent)
    header("Accept-Language", "en-US,en;q=0.9")
    header("X-Super-Properties", rest.getSuperProperties())
    fingerprint?.let { header("X-Fingerprint", it) }
    header("Origin", "https://discord.com")
    header("Referer", "https://discord.com/login")
}

fun createHttpClient() = HttpClient(httpClientEngine()) {
    install(HttpCookies)
    // Discord rate limits: wait out Retry-After and try again instead of failing the call.
    // Only ever a 429 from Discord; nothing else is retried.
    install(HttpRequestRetry) {
        maxRetries = 3
        retryIf { request, response ->
            response.status.value == 429 && request.url.host.endsWith("discord.com")
        }
        retryOnExceptionIf { _, _ -> false }
        exponentialDelay(respectRetryAfterHeader = true)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15000
        connectTimeoutMillis = 10000
        socketTimeoutMillis = 15000
    }
    install(Logging) {
        logger = SharedLogging.ktorLogger
        level = if (SharedLogging.debugEnabled) LogLevel.INFO else LogLevel.NONE
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
    }
    install(WebSockets)
    
    expectSuccess = false
}

/** Desktop client constants shared by REST and the gateway so both report the same client. */
const val DESKTOP_CLIENT_VERSION = "0.0.398"
const val DESKTOP_CLIENT_BUILD = 575562

/**
 * The desktop user agent for the OS we actually run on. REST always claimed Linux while the
 * gateway reported the real OS, an inconsistent fingerprint for anti-abuse to notice.
 */
fun desktopUserAgent(): String {
    val os = when (getPlatformName()) {
        "windows" -> "Windows NT 10.0; Win64; x64"
        "macos" -> "Macintosh; Intel Mac OS X 10_15_7"
        else -> "X11; Linux x86_64"
    }
    return "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) discord/$DESKTOP_CLIENT_VERSION Chrome/138.0.7204.251 Electron/37.6.0 Safari/537.36"
}

