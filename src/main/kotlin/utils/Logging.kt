package utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.defaultSerializer
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


// todo: to grpc?
@ExperimentalSerializationApi
@ExperimentalTime
@FlowPreview
object Logging {

    // only for tests
    suspend fun logs(resourceNames: List<String>, filter: String?, limit: Int?, accessToken: String): List<LogEntry> {
        return httpClient().use { client ->
            val url = "https://logging.googleapis.com/v2/entries:list?access_token=$accessToken"
            val req = LogRequest(resourceNames, filter, "timestamp desc", limit, null)
            client.post<LogResponse>(url) {
                body = defaultSerializer().write(req)
            }.entries
        }
    }

    // todo: https://cloud.google.com/logging/docs/reference/v2/rest/v2/entries/tail
    suspend fun logStream(resourceNames: List<String>, from: Instant, filter: String?, accessToken: String, keepGoing: (List<LogEntry>) -> Boolean): Flow<LogEntry> {
        val url = "https://logging.googleapis.com/v2/entries:list?access_token=$accessToken"

        val client = httpClient()

        return fetch(client, url, null, resourceNames, from, filter, keepGoing).onCompletion {
            client.close()
        }
    }

    suspend fun fetch(client: HttpClient, url: String, pageToken: String?, resourceNames: List<String>, from: Instant, filter: String?, keepGoing: (List<LogEntry>) -> Boolean): Flow<LogEntry> {
        val pageSize = 50
        val pollInterval = 1.seconds

        val filterWithFrom = if (filter != null) {
            "timestamp > \"$from\" AND $filter"
        } else {
            "timestamp > \"$from\""
        }

        val req = LogRequest(resourceNames, filterWithFrom, null, pageSize, pageToken)
        val res = client.post<LogResponse>(url) {
            body = defaultSerializer().write(req)
        }

        return if (keepGoing(res.entries)) {
            res.entries.asFlow().onCompletion {
                delay(pollInterval)

                // do not change the from when there is a nextPageToken
                val newFrom = if (res.nextPageToken != null) {
                    from
                } else {
                    res.entries.lastOrNull()?.timestamp ?: from
                }

                emitAll(fetch(client, url, res.nextPageToken, resourceNames, newFrom, filter, keepGoing))
            }
        } else {
            res.entries.asFlow()
        }
    }

    // only for tests
    suspend fun logWrite(entries: List<LogEntry>, accessToken: String): HttpResponse {
        return httpClient().use { client ->
            val url = "https://logging.googleapis.com/v2/entries:write?access_token=$accessToken"
            val req = LogWriteRequest(entries)
            client.post(url) {
                body = defaultSerializer().write(req)
            }
        }
    }

    @Serializable
    data class LogRequest(val resourceNames: List<String>, val filter: String?, val orderBy: String?, val pageSize: Int?, val pageToken: String?)

    @Serializable
    data class LogResponse(val entries: List<LogEntry> = emptyList(), val nextPageToken: String? = null)

    @Serializable
    data class LogWriteRequest(val entries: List<LogEntry>)

    @ExperimentalSerializationApi
    object InstantSerializer : KSerializer<Instant> {
        override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
        override fun deserialize(decoder: Decoder) = Instant.parse(decoder.decodeString())
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(InstantSerializer::javaClass.name, PrimitiveKind.STRING)
    }

    @ExperimentalSerializationApi
    @Serializable
    data class LogEntry(val logName: String, val resource: MonitoredResource, val jsonPayload: JsonPayload? = null, @Serializable(with = InstantSerializer::class) val timestamp: Instant)

    @Serializable
    data class MonitoredResource(val type: String, val labels: Map<String, String>)

    @Serializable
    data class JsonPayload(val message: String? = null)

}
