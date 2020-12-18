package utils

import java.io.IOException


object Runner {

    fun runRaw(cmd: String, maybeServiceAccount: String?, success: (Process) -> String): Result<String> {
        return runRaw(cmd, maybeServiceAccount, success, null)
    }

    fun runRaw(cmd: String, maybeServiceAccount: String?, success: (Process) -> String, failure: ((Process) -> String)?): Result<String> {
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

            val output = success(proc)

            if (proc.exitValue() == 0) {
                Result.success(output)
            } else {
                val err = if (failure != null) {
                    failure(proc)
                } else {
                    output
                }
                Result.failure(ProcessFailed(cmdWithMaybeServiceAccount, err))
            }
        } catch (e: IOException) {
            Result.failure(ProcessFailed(cmdWithMaybeServiceAccount, e))
        }
    }

    fun run(cmd: String, maybeServiceAccount: String?): Result<String> {
        return runRaw(cmd, maybeServiceAccount) { proc ->
            (proc.errorStream.bufferedReader().readText() + proc.inputStream.bufferedReader().readText()).trimEnd()
        }
    }

    fun json(cmd: String, maybeServiceAccount: String?): Result<String> {
        return runRaw(cmd, maybeServiceAccount, { it.inputStream.bufferedReader().readText().trim() }, { it.errorStream.bufferedReader().readText().trimEnd() })
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
