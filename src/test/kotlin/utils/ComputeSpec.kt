package utils

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldExist
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ComputeSpec : StringSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    "zones" {
        Compute.zones(projectId, "us-central1", maybeServiceAccount).getOrThrow() shouldExist { it.name == "us-central1-a" }
    }

    "machineTypes" {
        Compute.machineTypes(projectId, "us-central1-a", maybeServiceAccount).getOrThrow() shouldExist { it.name == "f1-micro" }
    }

})
