package utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object CloudRun {

    fun regions(projectId: String, maybeServiceAccount: String?): Result<Set<Region>> {
        val cmd = """
                  gcloud run regions list --project=$projectId --platform=managed --format=json
                  """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Region.serializer()), s).toSet()
        }
    }

    @Serializable
    data class Region(val displayName: String, val locationId: String)

}
