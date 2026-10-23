package me.lampu.lampcord.shared.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.http.Url
import me.lampu.lampcord.shared.settings.Settings

actual fun httpClientEngine(): HttpClientEngine = CIO.create {
    val proxyUrl = Settings.shared.httpProxyUrl.ifBlank {
        System.getenv("HTTPS_PROXY")
            ?: System.getenv("https_proxy")
            ?: System.getenv("ALL_PROXY")
            ?: System.getenv("all_proxy")
            ?: System.getenv("HTTP_PROXY")
            ?: System.getenv("http_proxy")
            ?: System.getProperty("https.proxyHost")?.let { host ->
                val port = System.getProperty("https.proxyPort") ?: "8080"
                "http://$host:$port"
            }
            ?: ""
    }
    if (proxyUrl.isNotBlank()) {
        try {
            val formattedUrl = if (!proxyUrl.startsWith("http://") && !proxyUrl.startsWith("https://")) {
                "http://$proxyUrl"
            } else proxyUrl
            proxy = ProxyBuilder.http(Url(formattedUrl))
        } catch (_: Exception) {}
    }
}
