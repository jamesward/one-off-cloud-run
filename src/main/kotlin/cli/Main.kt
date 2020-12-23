package cli

import org.beryx.textio.TextIoFactory
import utils.CloudRun
import utils.Compute
import utils.Projects
import utils.flatMap


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

    val latestRevision = if (service.status.latest == null) {
        terminal.println("No Cloud Run revisions found")
        terminal.abort()
        throw Exception()
    } else {
        CloudRun.revision(projectId, regionId, service.status.latest.revisionName)
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
        .withNumberedPossibleValues(machineTypes.filter { it.family == machineFamily }.sortedWith(Compute.MachineType.comparator).map { it.name })
        .read("Machine Type")

    val entrypointString = textIO.newStringInputReader()
        .withMinLength(0)
        .read("Container entrypoint (leave blank for default)")

    val entrypoint = if (entrypointString.isNullOrEmpty()) null else entrypointString

    fun askForArg(allArgs: List<String>): List<String> {
        val arg = textIO.newStringInputReader()
            .withMinLength(0)
            .read("Container arg (leave blank for continue)")

        return if (arg.isNullOrEmpty()) {
            allArgs
        } else {
            askForArg(allArgs + arg)
        }
    }

    val argsList = askForArg(emptyList())

    terminal.println("Starting job")

    // todo: numbered jobs?
    val instance = Compute.Instance(
        projectId,
        zone,
        machineType,
        service.spec.template.spec.containers.first().image,
        "${service.metadata.name}-${entrypoint ?: "default"}-job",
        entrypoint,
        argsList,
        service.spec.template.spec.containers.first().envMap,
        true,
        latestRevision.getOrNull()?.metadata?.annotations?.cloudSqlInstances,
        service.spec.template.spec.serviceAccountName)

    val describe = Compute.Instance.describe(instance)

    val createOrStart = if (describe.isFailure) {
        Compute.Instance.create(instance)
    } else {
        // todo: instance is running
        Compute.Instance.update(instance).flatMap {
            Compute.Instance.start(instance)
        }
    }

    terminal.println(createOrStart.toString())

    // todo: display logs

}
