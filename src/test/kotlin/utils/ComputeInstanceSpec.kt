package utils

import io.kotest.assertions.fail
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import utils.Compute.Instance
import kotlin.time.ExperimentalTime
import kotlin.time.minutes
import kotlin.time.seconds

// todo: afterTest Instance.delete
@ExperimentalSerializationApi
@FlowPreview
@ExperimentalTime
class ComputeInstanceSpec : WordSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")

    val regionId = System.getenv("REGION_ID") ?: fail("Must set REGION_ID env var")

    val dbPass = System.getenv("DB_PASS") ?: fail("Most set DB_PASS env var")
    val dbInstance = System.getenv("DB_INSTANCE") ?: fail("Most set DB_INSTANCE env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    val accessToken = Auth.accessTokenFromGcloud(maybeServiceAccount)

    val name = Instance.randomName()

    val zone = "us-central1-a"
    val machineType = "e2-medium"
    val defaultImage = "docker.io/hello-world"
    val testImage = "gcr.io/$projectId/one-off-cloud-run-test"

    val instance1 = Instance(projectId, zone, machineType, defaultImage, name, null, emptyList(), emptyMap(), false)
    val instance2 = instance1.copy(
        name = ""
    )
    val instance3 = instance1.copy(
        name = Instance.randomName(),
        serviceAccountName = maybeServiceAccount
    )
    val instance4 = instance1.copy(
        name = Instance.randomName(),
        containerImage = testImage,
        containerEnvs = mapOf("NAME" to "world"),
        containerEntrypoint = "/bin/sh",
        containerArgs = listOf("-c", "echo \"hello, \$NAME\"")
    )
    val instance5 = instance1.copy(
        name = Instance.randomName(),
        shutdownOnComplete = true
    )
    val instance6 = instance1.copy(
        name = Instance.randomName(),
        containerImage = testImage,
        containerEntrypoint = "psql",
        containerEnvs = mapOf("PGPASSWORD" to dbPass),
        containerArgs = listOf("-h", "/cloudsql/$projectId:$regionId:$dbInstance", "-U", "postgres", "-c", "SELECT 1"),
        instanceConnectionName = "$projectId:$regionId:$dbInstance"
    )

    "instance name" should {
        "not start with a number" {
            instance1.copy(name = "0").validName shouldBe "x-0"
        }
        "not be empty" {
            instance1.copy(name = null).validName.shouldNotBeBlank()
        }
        "fix invalid names" {
            instance1.copy(name = "a/b").validName shouldBe "a-b"
        }
    }

    "an instance" should {
        "be creatable" {
            val operation = Instance.create(instance1, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
        }

        "be creatable with an invalid name" {
            val operation = Instance.create(instance2, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
        }

        "be describable" {
            val operation = Instance.describe(instance1, maybeServiceAccount)
            operation.getOrThrow().status shouldBe "RUNNING"
        }

        "fail when trying to describe an non-existent instance" {
            val operation = Instance.describe(instance1.copy(name = Instance.randomName()), maybeServiceAccount)
            operation.isFailure shouldBe true
        }

        "be updatable" {
            val operation = Instance.update(instance1, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "done."
        }

        "be startable" {
            val operation = Instance.start(instance1, maybeServiceAccount)
            operation.getOrThrow() shouldInclude "Updated"
        }

        "be creatable with a custom service account" {
            val operation = Instance.create(instance3, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
            Instance.describe(instance3).getOrThrow().serviceAccounts.first().email shouldBe maybeServiceAccount
        }

        "be creatable with a custom entrypoint, args, and env vars" {
            val operation = Instance.create(instance4, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"

            eventually(2.minutes, 15.seconds) {
                Instance.logs(instance4, 500, accessToken, maybeServiceAccount).getOrThrow() shouldExist { it.jsonPayload?.message?.contains("hello, world") ?: false }
            }
        }

        "shutdown an instance after the docker process stops" {
            val createOperation = Instance.create(instance5, maybeServiceAccount)
            createOperation.getOrThrow() shouldEndWith "RUNNING"

            // wait for the shutdown script to run
            eventually(3.minutes, 15.seconds) {
                val operation = Instance.describe(instance5, maybeServiceAccount)
                val status = operation.getOrThrow().status
                status should (be("TERMINATED") or be("STOPPED"))
            }
        }

        "work with Cloud SQL" {
            val operation = Instance.create(instance6, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"

            eventually(2.minutes, 15.seconds) {
                Instance.logs(instance6, 500, accessToken, maybeServiceAccount).getOrThrow() shouldExist { it.jsonPayload?.message?.contains("(1 row)") ?: false }
            }
        }
    }

    afterSpec {
        // todo: run in parallel
        runBlocking {
            fun delete(instance: Instance) = async {
                if (Instance.describe(instance, maybeServiceAccount).isSuccess) {
                    Instance.delete(instance, maybeServiceAccount)
                }
            }

            val i1 = delete(instance1)
            val i2 = delete(instance2)
            val i3 = delete(instance3)
            val i4 = delete(instance4)
            val i5 = delete(instance5)
            val i6 = delete(instance6)

            i1.await()
            i2.await()
            i3.await()
            i4.await()
            i5.await()
            i6.await()
        }
    }

})
