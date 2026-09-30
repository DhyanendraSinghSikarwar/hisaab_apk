package com.hisaab.email.di

import com.hisaab.email.api.AccessTokenProvider
import com.hisaab.email.api.GmailApi
import com.hisaab.email.auth.GmailAuthManager
import com.hisaab.email.mime.PdfTextExtractor
import com.hisaab.email.sync.EmailSink
import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.email.sync.GmailSyncEngine
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmailModule {
    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(Android) {
        expectSuccess = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    @Provides
    fun accessTokens(auth: GmailAuthManager): AccessTokenProvider = auth

    @Provides
    @Singleton
    fun gmailApi(client: HttpClient, tokens: AccessTokenProvider): GmailApi = GmailApi(client, tokens)

    @Provides
    fun emailSink(repository: TransactionRepository): EmailSink = object : EmailSink {
        override suspend fun alreadyProcessed(ids: List<String>) = repository.alreadyProcessedEmails(ids)
        override suspend fun store(messages: List<IncomingMessage>, processed: List<ProcessedEmailEntity>) =
            repository.ingestBatch(messages, processed)
    }

    @Provides
    fun syncEngine(api: GmailApi, settings: GmailSettingsStore, sink: EmailSink, registry: ParserRegistry, pdf: PdfTextExtractor) =
        GmailSyncEngine(api, settings, sink, registry, pdf)
}
