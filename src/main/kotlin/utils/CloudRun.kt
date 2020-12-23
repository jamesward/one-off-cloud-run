package utils

import kotlinx.serialization.SerialName
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

    fun revision(projectId: String, regionId: String, revisionName: String, maybeServiceAccount: String? = null): Result<Revision> {
        val cmd = """
                  gcloud run revisions describe --project=$projectId --region=$regionId --platform=managed $revisionName
                  """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(Revision.serializer(), s)
        }
    }


    @Serializable
    data class Region(val displayName: String, val locationId: String)

    @Serializable
    data class Service(val metadata: ServiceMetadata, val spec: Spec, val status: Status)

    @Serializable
    data class ServiceMetadata(val name: String)

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

    @Serializable
    data class Status(val traffic: List<Traffic>) {
        val latest: Traffic? = traffic.find { it.latestRevision }
    }

    @Serializable
    data class Traffic(val latestRevision: Boolean, val revisionName: String)

    @Serializable
    data class Revision(val metadata: RevisionMetadata)

    @Serializable
    data class RevisionMetadata(val annotations: RevisionAnnotations)

    @Serializable
    data class RevisionAnnotations(@SerialName("run.googleapis.com/cloudsql-instances") val cloudSqlInstances: String? = null)

}
