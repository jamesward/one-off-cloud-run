package utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldNotBeEmpty


class ProjectsSpec : WordSpec({

    val maybeServiceAccount = System.getenv("SERVICE_ACCOUNT")

    "projects" should {
        "work" {
            Projects.list(maybeServiceAccount).getOrThrow().shouldNotBeEmpty()
        }
    }

})
