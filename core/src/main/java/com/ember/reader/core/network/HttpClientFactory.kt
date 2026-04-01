package com.ember.reader.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import timber.log.Timber

object HttpClientFactory {

    fun create(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
                followRedirects(true)
                followSslRedirects(true)
            }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    // Filter out Authorization headers to prevent credential leakage
                    if (message.contains("Authorization", ignoreCase = true)) return
                    Timber.tag("HttpClient").d(message)
                }
            }
            level = LogLevel.INFO
        }

        defaultRequest {
            header("User-Agent", "Ember/1.0 (Android)")
            header("Accept", "application/json")
        }

        expectSuccess = false
    }
}
