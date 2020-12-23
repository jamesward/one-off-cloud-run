package utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.features.logging.Logging
import kotlinx.serialization.json.Json

fun httpClient(): HttpClient {
    return HttpClient(CIO) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }
    }
}

fun <I, O> Result<I>.flatMap(f: (I) -> Result<O>): Result<O> {
    return try {
        val i = this.getOrThrow()
        f(i)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
