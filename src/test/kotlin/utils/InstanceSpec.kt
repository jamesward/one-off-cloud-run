package utils

import io.kotest.assertions.fail
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.*
import io.kotest.matchers.string.*
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.minutes
import kotlin.time.seconds

@ExperimentalTime
class InstanceSpec : WordSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    val name = Instance.randomName()

    val instanceInfo = Instance.Info(projectId, "us-central1-a", "n1-standard-1", "docker.io/hello-world", name)

    "instance name" should {
        "not start with a number" {
            instanceInfo.copy(name = "0").validName shouldBe "x-0"
        }
        "not be empty" {
            instanceInfo.copy(name = null).validName.shouldNotBeBlank()
        }
    }

    "an instance" should {
        "be creatable" {
            val operation = Instance.create(instanceInfo, null, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
        }
        "be creatable with an invalid name" {
            val invalidInstanceInfo = instanceInfo.copy(name = "")
            val operation = Instance.create(invalidInstanceInfo, null, maybeServiceAccount)
            Instance.delete(invalidInstanceInfo, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "RUNNING"
        }
        "be describable" {
            val operation = Instance.describe(instanceInfo, maybeServiceAccount)
            operation.getOrThrow() shouldInclude "status: RUNNING"
        }
        "fail when trying to describe an non-existent instance" {
            val operation = Instance.describe(instanceInfo.copy(name = Instance.randomName()), maybeServiceAccount)
            operation.isFailure shouldBe true
        }
        "be updatable" {
            val operation = Instance.update(instanceInfo, maybeServiceAccount)
            operation.getOrThrow() shouldEndWith "done."
        }
        "be startable" {
            val operation = Instance.start(instanceInfo, maybeServiceAccount)
            operation.getOrThrow() shouldInclude "Updated"
        }
    }

    "the shutdown script" should {
        "shutdown an instance after the docker process stops" {
            val tmpName = Instance.randomName()
            val tmpInstanceInfo = Instance.Info(projectId, "us-central1-a", "n1-standard-1", "docker.io/hello-world", tmpName)

            val script = javaClass.classLoader.getResource("scripts/shutdown-on-docker-exit.sh")?.toURI() ?: fail("Could not find scripts/shutdown-on-docker-exit.sh")

            val file = File(script)

            val createOperation = Instance.create(tmpInstanceInfo, file, maybeServiceAccount)
            createOperation.getOrThrow() shouldEndWith "RUNNING"

            // wait for the shutdown script to run
            eventually(2.minutes, 15.seconds) {
                val operation = Instance.describe(tmpInstanceInfo, maybeServiceAccount)
                operation.getOrThrow() should (include ("status: TERMINATED") or include ("status: STOPPING"))
            }

            // todo: delete even if the test failed
            Instance.delete(tmpInstanceInfo, maybeServiceAccount)
        }
    }

    afterSpec {
        if (Instance.describe(instanceInfo, maybeServiceAccount).isSuccess) {
            Instance.delete(instanceInfo, maybeServiceAccount)
        }
    }

})
