package utils

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds
import kotlin.time.seconds


@ExperimentalSerializationApi
@FlowPreview
@ExperimentalTime
class LoggingSpec : StringSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    val accessToken = Auth.accessTokenFromGcloud(maybeServiceAccount)

    val resourceNames = listOf("projects/$projectId")

    fun logs(): List<Logging.LogEntry> {
        val logName = "projects/$projectId/logs/${LoggingSpec::class.simpleName}"
        val resource = Logging.MonitoredResource("global", emptyMap())
        return listOf(Logging.LogEntry(logName, resource, Logging.JsonPayload("zxcv"), Instant.now()))
    }

    "logWrite" {
        Logging.logWrite(logs(), accessToken).status.value shouldBe 200
    }

    "logs" {
        Logging.logs(resourceNames, null, 10, accessToken).size shouldBe 10
    }

    "logStream" {
        val startTime = System.currentTimeMillis()
        val elapsed = { (System.currentTimeMillis() - startTime).milliseconds }
        val cond: (Logging.LogEntry) -> Boolean = { it.jsonPayload?.message?.contains("zxcv") ?: false }
        val flow = Logging.logStream(resourceNames, Instant.now(), null, accessToken) {
            it.find(cond) == null && elapsed() < 30.seconds
        }

        Logging.logWrite(logs(), accessToken)

        val entries = flow.toList()

        entries shouldExist(cond)
    }

    // todo: test to validate duplicates are not inserted

})


