import java.io.File
import java.io.IOException


object InstanceUtil {

    fun randomName() = (1..8).map { ('a'..'z').random() }.joinToString("")

    data class Info(
        val project: String,
        val zone: String,
        val machineType: String,
        val containerImage: String,
        val name: String? = null
    ) {
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
    }

    fun create(info: Info, maybeStartupFile: File?, maybeServiceAccount: String?): Result<String> {
        val baseCmd = """
                      gcloud compute instances create-with-container
                      ${info.validName}
                      --container-restart-policy=never
                      --no-restart-on-failure
                      --scopes=cloud-platform
                      --container-image=${info.containerImage}
                      --container-stdin
                      --container-tty
                      --zone=${info.zone}
                      --project=${info.project}
                      """

        val addStartupScript = maybeStartupFile?.let { it.exists() && it.isFile } ?: false

        val cmd = if (addStartupScript && maybeStartupFile != null) {
            "$baseCmd --metadata-from-file=startup-script=${maybeStartupFile.absolutePath}"
        } else {
            baseCmd
        }

        return run(cmd, maybeServiceAccount)
    }

    fun delete(info: Info, maybeServiceAccount: String?): Result<String> {
        val cmd = """
                  gcloud compute instances delete
                  ${info.validName}
                  --quiet
                  --zone=${info.zone}
                  --project=${info.project}
                  """

        return run(cmd, maybeServiceAccount)
    }

    fun describe(info: Info, maybeServiceAccount: String?): Result<String> {
        val cmd = """
                  gcloud compute instances describe
                  ${info.validName}
                  --zone=${info.zone}
                  --project=${info.project}
                  """

        return run(cmd, maybeServiceAccount)
    }

    fun update(info: Info, maybeServiceAccount: String?): Result<String> {
        val cmd = """
                  gcloud compute instances update-container
                  ${info.validName}
                  --container-image=${info.containerImage}
                  --zone=${info.zone}
                  --project=${info.project}
                  """

        return run(cmd, maybeServiceAccount)
    }

    fun start(info: Info, maybeServiceAccount: String?): Result<String> {
        val cmd = """
                 gcloud compute instances start
                 ${info.validName}
                 --zone=${info.zone}
                 --project=${info.project}
                 """

        return run(cmd, maybeServiceAccount)
    }

    fun run(cmd: String, maybeServiceAccount: String?): Result<String> {

        val cmdWithQuiet = cmd.trimIndent().replace("\n".toRegex(), " ").split("\\s".toRegex()).filter { it.isNotEmpty() } + "-q"

        val cmdWithMaybeServiceAccount = if (maybeServiceAccount != null) {
            cmdWithQuiet + "--impersonate-service-account=$maybeServiceAccount"
        } else {
            cmdWithQuiet
        }

        return try {
            val proc = ProcessBuilder(cmdWithMaybeServiceAccount)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            proc.waitFor()

            val output = (proc.errorStream.bufferedReader().readText() + proc.inputStream.bufferedReader().readText()).trimEnd()

            if (proc.exitValue() == 0) {
                Result.success(output)
            } else {
                Result.failure(ProcessFailed(cmdWithMaybeServiceAccount, output))
            }
        } catch (e: IOException) {
            Result.failure(ProcessFailed(cmdWithMaybeServiceAccount, e))
        }
    }

    class ProcessFailed : RuntimeException {
        constructor(cmd: List<String>, ex: Exception) : super(
            """
                |
                |Tried to run:
                |${cmd.joinToString(" ")}
            """.trimMargin(), ex
        )

        constructor(cmd: List<String>, out: String) : super(
            """
                |
                |Tried to run:
                |${cmd.joinToString(" ")}
                |
                |Resulted in:
                |$out
            """.trimMargin()
        )
    }

}
