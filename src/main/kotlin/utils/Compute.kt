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
        val name: String? = null) {

        val validName by lazy {
            val initialName =
                (name ?: containerImage.replace("[^0-9a-zA-Z]".toRegex(), "-").replace("--", "-")).toLowerCase()

            initialName.firstOrNull()?.let {
                if (Character.isAlphabetic(it.toInt()))
                    initialName
                else
                    "x-$initialName"
            } ?: randomName()
        }

        companion object {
            fun randomName() = (1..8).map { ('a'..'z').random() }.joinToString("")

            fun create(instance: Instance, maybeStartupFile: File?, maybeServiceAccount: String?): Result<String> {
                val baseCmd = """
                      gcloud compute instances create-with-container
                      ${instance.validName}
                      --container-restart-policy=never
                      --no-restart-on-failure
                      --scopes=cloud-platform
                      --container-image=${instance.containerImage}
                      --container-stdin
                      --container-tty
                      --zone=${instance.zone}
                      --project=${instance.project}
                      """

                val addStartupScript = maybeStartupFile?.let { it.exists() && it.isFile } ?: false

                val cmd = if (addStartupScript && maybeStartupFile != null) {
                    "$baseCmd --metadata-from-file=startup-script=${maybeStartupFile.absolutePath}"
                } else {
                    baseCmd
                }

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun delete(instance: Instance, maybeServiceAccount: String?): Result<String> {
                val cmd = """
                  gcloud compute instances delete
                  ${instance.validName}
                  --quiet
                  --zone=${instance.zone}
                  --project=${instance.project}
                  """

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun describe(instance: Instance, maybeServiceAccount: String?): Result<String> {
                val cmd = """
                  gcloud compute instances describe
                  ${instance.validName}
                  --zone=${instance.zone}
                  --project=${instance.project}
                  """

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun update(instance: Instance, maybeServiceAccount: String?): Result<String> {
                val cmd = """
                  gcloud compute instances update-container
                  ${instance.validName}
                  --container-image=${instance.containerImage}
                  --zone=${instance.zone}
                  --project=${instance.project}
                  """

                return Runner.run(cmd, maybeServiceAccount)
            }

            fun start(instance: Instance, maybeServiceAccount: String?): Result<String> {
                val cmd = """
                 gcloud compute instances start
                 ${instance.validName}
                 --zone=${instance.zone}
                 --project=${instance.project}
                 """

                return Runner.run(cmd, maybeServiceAccount)
            }
        }
    }

    fun zones(projectId: String, region: String, maybeServiceAccount: String?): Result<Set<Zone>> {
        val cmd = """
                 gcloud compute zones list --project=$projectId --filter=region=$region
                 """

        return Runner.json(cmd, maybeServiceAccount).map { s ->
            Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Zone.serializer()), s).toSet()
        }
    }

    fun machineTypes(projectId: String, zone: String, maybeServiceAccount: String?): Result<Set<MachineType>> {
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
