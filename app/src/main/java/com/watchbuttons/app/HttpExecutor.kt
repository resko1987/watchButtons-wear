package com.watchbuttons.app

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class HttpResult(
    val code: Int = -1,
    val body: String = "",
    val error: String? = null
)

object HttpExecutor {
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    fun execute(action: ButtonAction): HttpResult {
        val conn = try {
            URL(action.url.trim()).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return HttpResult(error = e.message ?: "invalid url")
        }
        return try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.useCaches = false
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty("User-Agent", "WatchButtons/1.0")
            if (action.method == "POST") {
                conn.requestMethod = "POST"
                conn.doOutput = true
                if (action.body.isNotBlank()) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                        it.write(action.body)
                    }
                }
            } else {
                conn.requestMethod = "GET"
            }
            val code = conn.responseCode
            val body = try {
                val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
            } catch (e: Exception) {
                ""
            }
            HttpResult(code = code, body = body)
        } catch (e: SocketTimeoutException) {
            HttpResult(error = "timeout ${READ_TIMEOUT_MS / 1000}s")
        } catch (e: Exception) {
            HttpResult(error = e.message ?: "unknown error")
        } finally {
            conn.disconnect()
        }
    }
}