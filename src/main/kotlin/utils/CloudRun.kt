package utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object CloudRun {

    fun regions(projectId: String, maybeServiceAccount: String? = null): Result<Set<Region>> {
        val cmd = """
                  gcloud run regions list --project=$projectId --platform=managed
                  """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Region.serializer()), s).toSet()
        }
    }

    fun services(projectId: String, regionId: String, maybeServiceAccount: String? = null): Result<Set<Service>> {
        val cmd = """
                  gcloud run services list --project=$projectId --region=$regionId --platform=managed
                  """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Service.serializer()), s).toSet()
        }
    }


    @Serializable
    data class Region(val displayName: String, val locationId: String)

    @Serializable
    data class Service(val metadata: Metadata, val spec: Spec)

    @Serializable
    data class Metadata(val name: String)

    @Serializable
    data class Spec(val template: Template)

    @Serializable
    data class Template(val spec: ContainerSpec)

    @Serializable
    data class ContainerSpec(val containers: List<Container>, val serviceAccountName: String? = null)

    @Serializable
    data class Container(val image: String, val env: List<Env> = emptyList()) {
        val envMap by lazy {
            env.map { it.name to it.value }.toMap()
        }
    }

    @Serializable
    data class Env(val name: String, val value: String)

}
