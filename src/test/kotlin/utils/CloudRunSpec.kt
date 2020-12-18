package utils

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldExist


class CloudRunSpec : WordSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    "regions" should {
        "work" {
            CloudRun.regions(projectId, maybeServiceAccount).getOrThrow() shouldExist { it.locationId == "us-central1" }
        }
    }

})
