package utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File


object Compute {

    data class Instance(
        val project: String,
        val zone: String,
        val machineType: String,
        val containerImage: String,
        val name: String? = null,
        val containerEntrypoint: String? = null,
        val containerArgs: List<String> = emptyList(),
        val containerEnvs: Map<String, String> = emptyMap(),
        val serviceAccountName: String? = null) {

        val validName by lazy {
            val initialName =name ?: containerImage

            initialName.firstOrNull()?.let {
                if (Character.isAlphabetic(it.toInt()))
                    initialName
                else
                    "x-$initialName"
            }?.replace("[^0-9a-zA-Z]".toRegex(), "-")?.replace("--", "-")?.toLowerCase() ?: randomName()
        }

        companion object {
            fun randomName() = (1..8).map { ('a'..'z').random() }.joinToString("")

            // todo: to json
            fun create(instance: Instance, maybeStartupFile: File?, maybeServiceAccount: String? = null): Result<String> {
                val baseCmd = Runner.cmdSplit("""
                      gcloud compute instances create-with-container
                      ${instance.validName}
                      --container-restart-policy=never
                      --no-restart-on-failure
                      --scopes=cloud-platform
                      --machine-type=${instance.machineType}
                      --container-image=${instance.containerImage}
                      --container-stdin
                      --container-tty
                      --zone=${instance.zone}
                      --project=${instance.project}
                      """)

                val addStartupScript = maybeStartupFile?.let { it.exists() && it.isFile } ?: false

                val cmd1 = if (addStartupScript && maybeStartupFile != null) {
                    baseCmd + "--metadata-from-file=startup-script=${maybeStartupFile.absolutePath}"
                } else {
                    baseCmd
                }

                val envsString = instance.containerEnvs.map {
                    "${it.key}=${it.value}"
                }

                val cmd2 = if (instance.containerEntrypoint != null) cmd1 + "--container-command=${instance.containerEntrypoint}" else cmd1
                val cmd3 = instance.containerArgs.fold(cmd2) { cmd, arg -> cmd + "--container-arg=$arg" }
                val cmd4 = if (envsString.isNotEmpty()) cmd3 + "--container-env=${envsString.joinToString(",")}" else cmd3
                val cmd5 = if (instance.serviceAccountName != null) cmd4 + "--service-account=${instance.serviceAccountName}" else cmd4

                return Runner.run(cmd5, maybeServiceAccount)
            }

            fun delete(instance: Instance, maybeServiceAccount: String? = null): Result<String> {
                val cmd = """
                  gcloud compute instances delete
                  ${instance.validName}
                  --quiet
                  --zone=${instance.zone}
                  --project=${instance.project}
                  """

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun describe(instance: Instance, maybeServiceAccount: String? = null): Result<InstanceDescription> {
                val cmd = """
                  gcloud compute instances describe
                  ${instance.validName}
                  --zone=${instance.zone}
                  --project=${instance.project}
                  """

                return Runner.json(cmd, maybeServiceAccount).map { s ->
                    Json { ignoreUnknownKeys = true }.decodeFromString(InstanceDescription.serializer(), s)
                }
            }

            fun update(instance: Instance, maybeServiceAccount: String? = null): Result<String> {
                val cmd = """
                  gcloud compute instances update-container
                  ${instance.validName}
                  --container-image=${instance.containerImage}
                  --zone=${instance.zone}
                  --project=${instance.project}
                  """

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun start(instance: Instance, maybeServiceAccount: String? = null): Result<String> {
                val cmd = """
                   gcloud compute instances start
                 ${instance.validName}
                 --zone=${instance.zone}
                 --project=${instance.project}
                 """

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun logs(instance: Instance, limit: Int?, maybeServiceAccount: String?): Result<List<LogEntry>> {
                return describe(instance, maybeServiceAccount).flatMap { instanceDescription ->
                    val filter = "resource.type=gce_instance AND logName=projects/${instance.project}/logs/cos_containers AND resource.labels.instance_id=${instanceDescription.id}"

                    val cmd1 = listOf(
                        "gcloud",
                        "logging",
                        "read",
                        "--project=${instance.project}",
                        filter
                    )

                    val cmd2 = if (limit != null) cmd1 + "--limit=$limit" else cmd1

                    Runner.json(cmd2, maybeServiceAccount).map { s ->
                        Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(LogEntry.serializer()), s)
                    }
                }
            }
        }

        @Serializable
        data class InstanceDescription(val id: String, val serviceAccounts: Set<ServiceAccount>, val status: String)

        @Serializable
        data class ServiceAccount(val email: String)

        @Serializable
        data class LogEntry(val jsonPayload: JsonPayload)

        @Serializable
        data class JsonPayload(val message: String)
    }

    fun zones(projectId: String, region: String, maybeServiceAccount: String? = null): Result<Set<Zone>> {
        val cmd = """
                 gcloud compute zones list --project=$projectId --filter=region=$region
                 """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Zone.serializer()), s).toSet()
        }
    }

    fun machineTypes(projectId: String, zone: String, maybeServiceAccount: String? = null): Result<Set<MachineType>> {
        val cmd = """
                 gcloud compute machine-types list --project=$projectId --zones=$zone
                 """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(MachineType.serializer()), s).toSet()
        }
    }

    @Serializable
    data class Zone(val name: String)

    @Serializable
    data class MachineType(val name: String)
}
