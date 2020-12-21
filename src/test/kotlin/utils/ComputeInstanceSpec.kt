package utils

import io.kotest.assertions.fail
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.*
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.string.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.Compute.Instance
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.minutes
import kotlin.time.seconds

// todo: afterTest Instance.delete
@ExperimentalTime
class ComputeInstanceSpec : WordSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    val name = Instance.randomName()

    val zone = "us-central1-a"
    val machineType = "e2-medium"
    val defaultImage = "docker.io/hello-world"
    val testImage = "gcr.io/$projectId/one-off-cloud-run-test"

    val instanceInfo1 = Instance(projectId, zone, machineType, defaultImage, name)
    val instanceInfo2 = instanceInfo1.copy(
        name = ""
    )
    val instanceInfo3 = instanceInfo1.copy(
        name = Instance.randomName(),
        serviceAccountName = maybeServiceAccount
    )
    val instanceInfo4 = instanceInfo1.copy(
        name = Instance.randomName(),
        containerImage = testImage,
        containerEnvs = mapOf("NAME" to "world"),
        containerEntrypoint = "/bin/sh",
        containerArgs = listOf("-c", "echo \"hello, \$NAME\"")
    )
    val instanceInfo5 = instanceInfo1.copy(
        name = Instance.randomName(),
        containerEntrypoint = "/bin/ls"
    )

    "instance name" should {
        "not start with a number" {
            instanceInfo1.copy(name = "0").validName shouldBe "x-0"
        }
        "not be empty" {
            instanceInfo1.copy(name = null).validName.shouldNotBeBlank()
        }
        "fix invalid names" {
            instanceInfo1.copy(name = "a/b").validName shouldBe "a-b"
        }
    }

    "an instance" should {
        "be creatable" {
            val operation = Instance.create(instanceInfo1, null, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
        }
        "be creatable with an invalid name" {
            val operation = Instance.create(instanceInfo2, null, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
        }
        "be describable" {
            val operation = Instance.describe(instanceInfo1, maybeServiceAccount)
            operation.getOrThrow().status shouldBe "RUNNING"
        }
        "fail when trying to describe an non-existent instance" {
            val operation = Instance.describe(instanceInfo1.copy(name = Instance.randomName()), maybeServiceAccount)
            operation.isFailure shouldBe true
        }
        "be updatable" {
            val operation = Instance.update(instanceInfo1, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "done."
        }
        "be startable" {
            val operation = Instance.start(instanceInfo1, maybeServiceAccount)
            operation.getOrThrow() shouldInclude "Updated"
        }
        "be creatable with a custom service account" {
            val operation = Instance.create(instanceInfo3, null, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
            Instance.describe(instanceInfo3).getOrThrow().serviceAccounts.first().email shouldBe maybeServiceAccount
        }
        "be creatable with a custom entrypoint, args, and env vars" {
            val operation = Instance.create(instanceInfo4, null, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"

            eventually(2.minutes, 15.seconds) {
                Instance.logs(instanceInfo4, 500, maybeServiceAccount).getOrThrow() shouldExist { it.jsonPayload.message.contains("hello, world") }
            }
        }
    }

    "the shutdown script" should {
        "shutdown an instance after the docker process stops" {
            val script = javaClass.classLoader.getResource("scripts/shutdown-on-docker-exit.sh")?.toURI() ?: fail("Could not find scripts/shutdown-on-docker-exit.sh")

            val file = File(script)

            val createOperation = Instance.create(instanceInfo5, file, maybeServiceAccount)
            createOperation.getOrThrow() shouldEndWith "RUNNING"

            // wait for the shutdown script to run
            eventually(2.minutes, 15.seconds) {
                val operation = Instance.describe(instanceInfo5, maybeServiceAccount)
                val status = operation.getOrThrow().status
                status should (be("TERMINATED") or be("STOPPED"))
            }
        }
    }

    afterSpec { _ ->
        runBlocking {
            listOf(instanceInfo1, instanceInfo2, instanceInfo3, instanceInfo4, instanceInfo5).map {
                async {
                    if (Instance.describe(it, maybeServiceAccount).isSuccess) {
                        Instance.delete(it, maybeServiceAccount)
                    }
                }
            }.forEach {
                it.await()
            }
        }
    }

})
