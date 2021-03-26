package cli

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import org.beryx.textio.TextIoFactory
import utils.*
import java.time.Instant
import kotlin.time.ExperimentalTime


@ExperimentalSerializationApi
@FlowPreview
@ExperimentalTime
fun main() {

    val textIO = TextIoFactory.getTextIO()

    val terminal = textIO.textTerminal

    fun <T> Result<T>.getOrError(): T {
        return this.getOrElse {
            terminal.println(it.message)
            terminal.abort()
            throw it
        }
    }

    // todo, in-escapable loop on job list
    // if no jobs go into create tui
    // ctrl-c exits create tui
    // view logs for job
    // stop job

    fun zoneToRegion(zone: String): String = zone.dropLast(2)

    data class UserInput(
        val project: String,
        val zone: String,
        val machineType: String,
        val service: CloudRun.Service,
        val entrypoint: String?,
        val args: List<String>
    ) {
        val region = zoneToRegion(zone)
    }

    fun askForUserInput(): UserInput {
        val projects = Projects.list().getOrError()

        val projectId = when {
            projects.isEmpty() -> {
                terminal.println("No projects found")
                terminal.abort()
                throw Exception()
            }
            projects.size == 1 -> {
                terminal.println("Using Project: ${projects.first().projectId}")
                projects.first().projectId
            }
            else -> {
                textIO.newStringInputReader()
                    .withNumberedPossibleValues(projects.map { it.projectId })
                    .read("Project")
            }
        }

        terminal.println("Create a new One-Off Cloud Run job")

        val regions = CloudRun.regions(projectId).getOrError()

        val regionId = textIO.newStringInputReader()
            .withNumberedPossibleValues(regions.map { it.locationId })
            .read("Region")

        val services = CloudRun.services(projectId, regionId).getOrError()

        val service = if (services.isEmpty()) {
            terminal.println("No Cloud Run services found")
            terminal.abort()
            throw Exception()
        } else {
            val serviceName = textIO.newStringInputReader()
                .withNumberedPossibleValues(services.map { it.metadata.name })
                .read("Cloud Run Service")

            services.find { it.metadata.name == serviceName }!!
        }

        val zones = Compute.zones(projectId, regionId).getOrError()

        val zone = textIO.newStringInputReader()
            .withNumberedPossibleValues(zones.map { it.name })
            .read("Zone")

        val machineTypes = Compute.machineTypes(projectId, zone).getOrError()

        val machineFamilies = machineTypes.map { it.family }.distinct()

        val machineFamily = textIO.newStringInputReader()
            .withNumberedPossibleValues(machineFamilies)
            .read("Machine Family")

        val machineType = textIO.newStringInputReader()
            .withNumberedPossibleValues(machineTypes.filter { it.family == machineFamily }
                .sortedWith(Compute.MachineType.comparator).map { it.name })
            .read("Machine Type")

        val entrypointString = textIO.newStringInputReader()
            .withMinLength(0)
            .read("Container entrypoint (leave blank for default)")

        val entrypoint = if (entrypointString.isNullOrEmpty()) null else entrypointString

        fun askForArg(allArgs: List<String>): List<String> {
            val arg = textIO.newStringInputReader()
                .withMinLength(0)
                .read("Container arg (leave blank to continue)")

            return if (arg.isNullOrEmpty()) {
                allArgs
            } else {
                askForArg(allArgs + arg)
            }
        }

        val argsList = askForArg(emptyList())

        return UserInput(projectId, zone, machineType, service, entrypoint, argsList)
    }

    val userInput: UserInput = System.getenv()["PROJECT_ID"]?.let { project ->
        System.getenv()["ZONE"]?.let { zone ->
            System.getenv()["SERVICE"]?.let { serviceName ->
                System.getenv()["MACHINE_TYPE"]?.let { machineType ->
                    val service = CloudRun.services(project, zoneToRegion(zone)).getOrError().first { it.metadata.name == serviceName }
                    UserInput(project, zone, machineType, service, System.getenv()["ENTRYPOINT"], System.getenv()["ARGS"]?.split(',').orEmpty())
                }
            }
        }
    } ?: askForUserInput()

    val latestRevision = if (userInput.service.status.latest == null) {
        terminal.println("No Cloud Run revisions found")
        terminal.abort()
        throw Exception()
    } else {
        CloudRun.revision(userInput.project, userInput.region, userInput.service.status.latest.revisionName)
    }.getOrError()

    terminal.println("Starting Instance")

    val start = Instant.now()

    // todo: numbered jobs?
    val instance = Compute.Instance(
        userInput.project,
        userInput.zone,
        userInput.machineType,
        userInput.service.spec.template.spec.containers.first().image,
        "${userInput.service.metadata.name}-${userInput.entrypoint ?: "default"}-job",
        userInput.entrypoint,
        userInput.args,
        userInput.service.spec.template.spec.containers.first().envMap,
        true,
        latestRevision.metadata.annotations.cloudSqlInstances,
        latestRevision.metadata.annotations.vpcAccessConnector,
        userInput.service.spec.template.spec.serviceAccountName)

    val describe = Compute.Instance.describe(instance)

    val createOrStart = if (describe.isFailure) {
        Compute.Instance.create(instance)
    } else {
        // todo: instance is running
        Compute.Instance.update(instance).flatMap {
            Compute.Instance.start(instance)
        }
    }

    createOrStart.fold({
        terminal.println("Instance Started.  Logs:")

        val runningInstance = Compute.Instance.describe(instance).getOrThrow()

        runBlocking {
            val accessToken = Auth.accessToken()
            val filter = "resource.type=gce_instance AND logName=projects/${instance.project}/logs/cos_containers AND resource.labels.instance_id=${runningInstance.id}"
            Logging.logStream(listOf("projects/${instance.project}"), start, filter, accessToken) {
                Compute.Instance.describe(instance).getOrNull()?.status == "RUNNING"
            }.collect {
                it.jsonPayload?.message?.let { msg ->
                    terminal.print(msg)
                }
            }
            terminal.println("Process Ended")
        }
    }, {
        terminal.println("Could not start instance: ${it.message}")
        terminal.abort()
        throw Exception()
    })

}
