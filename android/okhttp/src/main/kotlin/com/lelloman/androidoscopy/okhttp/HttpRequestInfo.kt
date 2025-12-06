package com.lelloman.androidoscopy.okhttp

/**
 * Represents a captured HTTP request/response pair.
 */
data class HttpRequestInfo(
    val id: String,
    val timestamp: Long,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val requestHeaders: Map<String, String>,
    val requestBodySize: Long,
    val responseCode: Int?,
    val responseMessage: String?,
    val responseHeaders: Map<String, String>,
    val responseBodySize: Long,
    val durationMs: Long,
    val error: String?
) {
    val isSuccess: Boolean
        get() = error == null && responseCode != null && responseCode in 200..299

    val isError: Boolean
        get() = error != null || (responseCode != null && responseCode >= 400)

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "timestamp" to timestamp,
        "method" to method,
        "url" to url,
        "host" to host,
        "path" to path,
        "request_headers" to requestHeaders,
        "request_body_size" to requestBodySize,
        "response_code" to responseCode,
        "response_message" to responseMessage,
        "response_headers" to responseHeaders,
        "response_body_size" to responseBodySize,
        "duration_ms" to durationMs,
        "error" to error,
        "is_success" to isSuccess,
        "is_error" to isError
    )
}
