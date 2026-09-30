package com.hisaab.email.api

import kotlinx.serialization.Serializable

// The subset of the Gmail REST API v1 this app reads. Unknown fields are ignored.

@Serializable data class Profile(val emailAddress: String? = null, val historyId: String)

@Serializable data class MessageRef(val id: String, val threadId: String? = null)

@Serializable data class ListMessagesResponse(
    val messages: List<MessageRef> = emptyList(),
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int = 0,
)

@Serializable data class Header(val name: String, val value: String)

@Serializable data class MessagePartBody(val attachmentId: String? = null, val size: Int = 0, val data: String? = null)

@Serializable data class MessagePart(
    val partId: String? = null,
    val mimeType: String? = null,
    val filename: String? = null,
    val headers: List<Header> = emptyList(),
    val body: MessagePartBody? = null,
    val parts: List<MessagePart> = emptyList(),
)

@Serializable data class GmailMessage(
    val id: String,
    val threadId: String? = null,
    val labelIds: List<String> = emptyList(),
    val snippet: String? = null,
    val historyId: String? = null,
    /** Epoch millis, as a string. */
    val internalDate: String? = null,
    val payload: MessagePart? = null,
)

@Serializable data class HistoryMessageAdded(val message: MessageRef)

@Serializable data class HistoryRecord(val id: String, val messagesAdded: List<HistoryMessageAdded> = emptyList())

@Serializable data class ListHistoryResponse(
    val history: List<HistoryRecord> = emptyList(),
    val nextPageToken: String? = null,
    val historyId: String? = null,
)

@Serializable data class Attachment(val size: Int = 0, val data: String? = null)
