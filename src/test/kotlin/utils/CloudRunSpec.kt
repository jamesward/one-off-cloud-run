package utils

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe


class CloudRunSpec : StringSpec({

    val projectId = System.getenv("PROJECT_ID") ?: fail("Must set PROJECT_ID env var")
    val regionId = System.getenv("REGION_ID") ?: fail("Must set REGION_ID env var")

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    "regions" {
        CloudRun.regions(projectId, maybeServiceAccount).getOrThrow() shouldExist { it.locationId == regionId }
    }

    "services" {
        CloudRun.services(projectId, regionId, maybeServiceAccount).getOrThrow().shouldNotBeEmpty()
    }

    "revision" {
        val serviceName = "one-off-cloud-run-test"
        val service = CloudRun.services(projectId, regionId, maybeServiceAccount).getOrThrow().find { it.metadata.name == serviceName }

        if (service == null) {
            fail("Could not find service $serviceName")
        } else {
            val latestRevision = service.status.latest

            if (latestRevision == null) {
                fail("Could not get latest revision for $serviceName")
            } else {
                val revision = CloudRun.revision(projectId, regionId, latestRevision.revisionName, maybeServiceAccount).getOrThrow()
                revision.metadata.annotations.cloudSqlInstances shouldBe("$projectId:$regionId:one-off-cloud-run-test")
            }
        }
    }

})
