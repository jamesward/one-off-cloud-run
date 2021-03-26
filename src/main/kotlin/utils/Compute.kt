package utils

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.ExperimentalTime


object Compute {

    @FlowPreview
    @ExperimentalSerializationApi
    @ExperimentalTime
    data class Instance(
        val project: String,
        val zone: String,
        val machineType: String,
        val containerImage: String,
        val name: String? = null,
        val containerEntrypoint: String? = null,
        val containerArgs: List<String> = emptyList(),
        val containerEnvs: Map<String, String> = emptyMap(),
        val shutdownOnComplete: Boolean = true,
        val instanceConnectionName: String? = null,
        val vpcAccessConnector: String? = null,
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

        @ExperimentalSerializationApi
        @FlowPreview
        @ExperimentalTime
        companion object {
            fun randomName() = (1..8).map { ('a'..'z').random() }.joinToString("")

            // todo: to json
            fun create(instance: Instance, maybeServiceAccount: String? = null): Result<String> {
                val startupScriptResource = this::class.java.classLoader.getResource("scripts/startup-script.sh")?.toURI()

                return if (startupScriptResource == null) {
                    Result.failure(Exception("Could not find startup-script.sh"))
                } else {
                    val startupScript = if (startupScriptResource.scheme == "jar") {
                        startupScriptResource.toURL().openStream().use { input ->
                            val f = File("/tmp/startup-script.sh")
                            if (f.exists()) {
                                f.delete()
                            }
                            f.outputStream().use { input.copyTo(it) }
                            f.setExecutable(true)
                            f.toURI()
                        }
                    } else {
                        startupScriptResource
                    }

                    val startupScriptFile = File(startupScript)

                    if (!startupScriptFile.exists()) {
                        Result.failure(Exception("Could not find $startupScriptFile"))
                    } else {

                        val baseCmd = Runner.cmdSplit(
                            """
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
                                --metadata-from-file=startup-script=${startupScriptFile.absolutePath}
                            """
                        )

                        val envsString = instance.containerEnvs.map {
                            "${it.key}=${it.value}"
                        }

                        val meta1 = emptyMap<String, String>()
                        val meta2 = if (instance.shutdownOnComplete) meta1 + ("POWER_OFF_WHEN_DONE" to "true") else meta1
                        val meta3 = if (instance.instanceConnectionName != null) meta2 + ("INSTANCE_CONNECTION_NAME" to instance.instanceConnectionName) else meta2

                        val metadataString = meta3.map { "${it.key}=${it.value}" }

                        val maybeNetwork = instance.vpcAccessConnector?.let { vpcAccessConnector ->
                            // todo: terrible parsing
                            val parts = vpcAccessConnector.split('/')
                            networkVpcAccessConnector(parts[1], parts[3], parts[5])
                        }?.getOrNull()

                        val cmd1 = if (instance.containerEntrypoint != null) baseCmd + "--container-command=${instance.containerEntrypoint}" else baseCmd
                        val cmd2 = instance.containerArgs.fold(cmd1) { cmd, arg -> cmd + "--container-arg=$arg" }
                        val cmd3 = if (envsString.isNotEmpty()) cmd2 + "--container-env=${envsString.joinToString(",")}" else cmd2
                        val cmd4 = if (instance.serviceAccountName != null) cmd3 + "--service-account=${instance.serviceAccountName}" else cmd3
                        val cmd5 = if (instance.instanceConnectionName != null) cmd4 + "--container-mount-host-path=host-path=/mnt/stateful_partition/cloudsql,mount-path=/cloudsql" else cmd4
                        val cmd6 = if (maybeNetwork != null) cmd5 + "--network=${maybeNetwork.network}" else cmd5
                        val cmd7 = if (metadataString.isNotEmpty()) cmd6 + "--metadata=${metadataString.joinToString(",")}" else cmd5

                        Runner.run(cmd7, maybeServiceAccount)
                    }
                }
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

            // todo: update all params
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

            // just for tests
            fun logs(instance: Instance, limit: Int?, accessToken: String, maybeServiceAccount: String?): Result<List<Logging.LogEntry>> {
                return describe(instance, maybeServiceAccount).map { instanceDescription ->
                    val filter = "resource.type=gce_instance AND logName=projects/${instance.project}/logs/cos_containers AND resource.labels.instance_id=${instanceDescription.id}"

                    // todo: coroutine to Result
                    runBlocking {
                        Logging.logs(listOf("projects/${instance.project}"), filter, limit, accessToken)
                    }
                }
            }
        }

        @Serializable
        data class InstanceDescription(val id: String, val serviceAccounts: Set<ServiceAccount>, val status: String)

        @Serializable
        data class ServiceAccount(val email: String)
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

    fun networkVpcAccessConnector(projectId: String, region: String, name: String, maybeServiceAccount: String? = null): Result<NetworkVpcAccessConnector> {
        val cmd = """
                  gcloud compute networks vpc-access connectors describe $name --project=$projectId --region=$region
                  """.trimIndent()

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(NetworkVpcAccessConnector.serializer(), s)
        }
    }

    @Serializable
    data class Zone(val name: String)

    @Serializable
    data class MachineType(val name: String) {
        val parts by lazy { name.split("-") }

        val family by lazy { parts[0] }
        val classification by lazy { parts[1] }
        val mem by lazy { parts.getOrNull(2) }

        companion object {
            fun classificationPriority(classification: String): Int {
                return when(classification) {
                    "micro" -> 0
                    "small" -> 1
                    "medium" -> 2
                    "standard" -> 3
                    "highcpu" -> 4
                    "highgpu" -> 5
                    "highmem" -> 6
                    "ultracpu" -> 7
                    "ultagpu" -> 8
                    "ultamem" -> 9
                    "megacpu" -> 10
                    "megagpu" -> 11
                    "megamem" -> 12
                    else -> 100
                }
            }

            fun memPriority(mem: String?): Int {
                return mem?.replace("[^0-9]".toRegex(), "")?.toInt() ?: -1
            }

            val comparator = compareBy<MachineType> { classificationPriority(it.classification) }.thenBy { memPriority(it.mem) }
        }
    }

    @Serializable
    data class NetworkVpcAccessConnector(val network: String)
}
