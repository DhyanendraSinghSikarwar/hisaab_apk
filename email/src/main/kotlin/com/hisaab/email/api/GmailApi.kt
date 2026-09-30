package com.hisaab.email.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import java.io.IOException

/** Supplies an OAuth access token with the gmail.readonly scope. */
interface AccessTokenProvider {
    /** Null when the user must sign in again. */
    suspend fun token(forceRefresh: Boolean = false): String?
}

class AuthRequiredException : IOException("Gmail access needs to be granted again")

/** Stored historyId is older than Gmail keeps (about a week): the caller must do a date-bounded full sync. */
class HistoryExpiredException : IOException("Gmail history id expired")

class GmailHttpException(val status: Int, message: String) : IOException(message)

/**
 * Direct calls to the Gmail REST API over Ktor, instead of the much larger google-api-client.
 * Retries 429 and 5xx with exponential backoff and refreshes the token once on 401.
 */
class GmailApi(
    private val client: HttpClient,
    private val tokens: AccessTokenProvider,
    private val baseUrl: String = "https://gmail.googleapis.com/gmail/v1/users/me",
) {
    suspend fun profile(): Profile = call("$baseUrl/profile").body()

    suspend fun listMessages(query: String, pageToken: String?, pageSize: Int = 100): ListMessagesResponse =
        call("$baseUrl/messages") {
            parameter("q", query)
            parameter("maxResults", pageSize)
            pageToken?.let { parameter("pageToken", it) }
        }.body()

    suspend fun message(id: String): GmailMessage = call("$baseUrl/messages/$id") { parameter("format", "full") }.body()

    suspend fun attachment(messageId: String, attachmentId: String): Attachment =
        call("$baseUrl/messages/$messageId/attachments/$attachmentId").body()

    suspend fun history(startHistoryId: String, pageToken: String?): ListHistoryResponse {
        val response = call("$baseUrl/history", allow404 = true) {
            parameter("startHistoryId", startHistoryId)
            parameter("historyTypes", "messageAdded")
            parameter("maxResults", 500)
            pageToken?.let { parameter("pageToken", it) }
        }
        if (response.status == HttpStatusCode.NotFound) throw HistoryExpiredException()
        return response.body()
    }

    // Named "configure", not "build": inside the request block, build() would resolve to HttpRequestBuilder.build().
    private suspend fun call(url: String, allow404: Boolean = false, configure: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        var token = tokens.token() ?: throw AuthRequiredException()
        var refreshed = false
        var attempt = 0
        while (true) {
            val response = client.get(url) {
                bearerAuth(token)
                configure()
            }
            val code = response.status.value
            when {
                code in 200..299 -> return response
                code == 404 && allow404 -> return response
                code == 401 && !refreshed -> {
                    token = tokens.token(forceRefresh = true) ?: throw AuthRequiredException()
                    refreshed = true
                }
                code == 401 || code == 403 -> throw AuthRequiredException()
                (code == 429 || code >= 500) && attempt < MAX_RETRIES -> delay(BACKOFF_MS shl attempt++)
                else -> throw GmailHttpException(code, "Gmail API $code for ${url.substringAfterLast("/me")}")
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val BACKOFF_MS = 500L
    }
}
