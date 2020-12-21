package cli

import org.beryx.textio.TextIoFactory
import utils.CloudRun
import utils.Compute
import utils.Projects
import java.io.File


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



    val projects = Projects.list().getOrError()

    val projectId = when {
        projects.isEmpty() -> {
            terminal.println("No projects found")
            terminal.abort()
            throw Exception()
        }
        projects.size == 1 -> {
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

    val machineType = textIO.newStringInputReader()
        .withNumberedPossibleValues(machineTypes.map { it.name })
        .read("Machine Type")

    val entrypointString = textIO.newStringInputReader()
        .read("Container entrypoint (leave blank for default)")

    val entrypoint = if (entrypointString.isNullOrEmpty()) null else entrypointString

    val args = textIO.newStringInputReader()
        .read("Container args")

    terminal.println("Starting job")

    // todo: numbered jobs?
    val instance = Compute.Instance(
        projectId,
        zone,
        machineType,
        service.spec.template.spec.containers.first().image,
        "${service.metadata.name}-${entrypoint ?: "default"}-job",
        entrypoint,
        args.split("\\s".toRegex()),
        service.spec.template.spec.containers.first().envMap,
        service.spec.template.spec.serviceAccountName)

    val startupfile = {}::class.java.classLoader.getResource("scripts/shutdown-on-docker-exit.sh")?.toURI()

    if (startupfile != null) {
        Compute.Instance.create(instance, File(startupfile))
    } else {
        terminal.println("Could not find shutdown script")
        terminal.abort()
    }
}
