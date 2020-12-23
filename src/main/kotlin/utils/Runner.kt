package utils

import java.io.IOException

fun <I, O> Result<I>.flatMap(f: (I) -> Result<O>): Result<O> {
    return try {
        val i = this.getOrThrow()
        f(i)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

object Runner {

    fun cmdSplit(cmd: String): List<String> {
        return cmd.trimIndent().replace("\n".toRegex(), " ").split("\\s".toRegex())
    }

    fun runRaw(cmd: List<String>, maybeServiceAccount: String?, success: (Process) -> String): Result<String> {
        return runRaw(cmd, maybeServiceAccount, success, null)
    }

    fun runRaw(cmd: List<String>, maybeServiceAccount: String?, success: (Process) -> String, failure: ((Process) -> String)?): Result<String> {
        val cmdWithQuiet = cmd.map { it.trim() }.filter { it.isNotEmpty() } + "-q"

        val cmdWithMaybeServiceAccount = if (maybeServiceAccount != null) {
            cmdWithQuiet + "--impersonate-service-account=$maybeServiceAccount"
        } else {
            cmdWithQuiet
        }

        return try {
            val proc = ProcessBuilder(cmdWithMaybeServiceAccount).start()

            val output = success(proc)

            proc.waitFor()

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

    fun run(cmd: List<String>, maybeServiceAccount: String?): Result<String> {
        return runRaw(cmd, maybeServiceAccount) { proc ->
            (proc.errorStream.bufferedReader().readText() + proc.inputStream.bufferedReader().readText()).trimEnd()
        }
    }

    fun run(cmd: String, maybeServiceAccount: String?): Result<String> {
        return run(cmdSplit(cmd), maybeServiceAccount)
    }

    // todo: maybe json parsing functor?
    fun json(cmd: List<String>, maybeServiceAccount: String?): Result<String> {
        val cmdWithJson = cmd + "--format=json"
        return runRaw(cmdWithJson, maybeServiceAccount, { it.inputStream.bufferedReader().readText().trim() }, { it.errorStream.bufferedReader().readText().trimEnd() })
    }

    fun json(cmd: String, maybeServiceAccount: String?): Result<String> {
        return json(cmdSplit(cmd), maybeServiceAccount)
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
