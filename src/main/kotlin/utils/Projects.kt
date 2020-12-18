package utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object Projects {

    fun list(maybeServiceAccount: String?): Result<Set<Project>> {
        val cmd = """
                  gcloud projects list
                  """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Project.serializer()), s).toSet()
        }
    }


    @Serializable
    data class Project(val name: String, val lifecycleState: String, val projectId: String)

}
