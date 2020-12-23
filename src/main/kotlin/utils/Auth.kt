package utils

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Auth {

    fun accessTokenFromGcloud(maybeServiceAccount: String?): String {
        @Serializable
        data class AccessToken(val token: String)

        return Runner.json("gcloud auth print-access-token", maybeServiceAccount).map {
            Json { ignoreUnknownKeys = true }.decodeFromString(AccessToken.serializer(), it)
        }.getOrThrow().token
    }

    suspend fun accessTokenFromMetadata(client: HttpClient): String {
        @Serializable
        data class TokenResponse(@SerialName("access_token") val accessToken: String)

        return client.get<TokenResponse>("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token") {
            header("Metadata-Flavor", "Google")
        }.accessToken
    }

    suspend fun accessToken(maybeServiceAccount: String? = null): String {
        return try {
            httpClient().use {
                accessTokenFromMetadata(it)
            }
        } catch (e: Exception) {
            accessTokenFromGcloud(maybeServiceAccount)
        }
    }

}
