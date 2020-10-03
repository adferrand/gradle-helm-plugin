package org.unbrokendome.gradle.plugins.helm.command.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.unbrokendome.gradle.plugins.helm.model.ChartDescriptor
import org.unbrokendome.gradle.plugins.helm.model.ChartDescriptorYaml


/**
 * Packages a chart into a versioned chart archive file. Corresponds to the `helm package` CLI command.
 *
 * The chart name and version need to be known at configuration time to determine the task outputs. If they are not
 * specified explicitly using the [chartName] and [chartVersion] properties, the task will parse the `Chart.yaml`
 * file and extract the missing information from there.
 */
@Suppress("LeakingThis")
abstract class HelmPackage : AbstractHelmCommandTask() {

    internal companion object {

        /**
         * Returns the name of the packaged chart file, according to the pattern
         * `<name>-<version>.tgz`.
         */
        fun packagedChartFileName(chartName: Provider<String>, chartVersion: Provider<String>): Provider<String> =
            chartName.flatMap { name ->
                chartVersion.map { version -> "${name}-${version}.tgz" }
            }
    }

    /**
     * Set the appVersion on the chart to this version.
     *
     * Corresponds to the `--app-version` CLI option.
     */
    @get:[Input Optional]
    abstract val appVersion: Property<String>


    /**
     * Update dependencies from "Chart.yaml" to dir "charts/" before packaging.
     *
     * Corresponds to the `--dependency-update` CLI option.
     */
    @get:Input
    abstract val updateDependencies: Property<Boolean>


    /**
     * The directory that contains the sources for the Helm chart.
     */
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty


    /**
     * The parsed Chart.yaml file. Implemented as a lazy [Provider] so we only parse the file when necessary.
     */
    private val chartDescriptor: Provider<ChartDescriptor> =
        sourceDir.file("Chart.yaml")
            .let { ChartDescriptorYaml.loading(it) }


    /**
     * The name of the chart.
     *
     * If not set, the chart name will be read from the _Chart.yaml_ file in the source directory.
     */
    @get:Input
    abstract val chartName: Property<String>


    /**
     * The version of the chart.
     *
     * If not set, the chart version will be read from the _Chart.yaml_ file in the source directory.
     */
    @get:Input
    abstract val chartVersion: Property<String>


    /**
     * Location to write the chart archive.
     *
     * Default destination is `helm/charts/` under the project's build directory.
     */
    @get:Internal("Represented as part of packageFile")
    abstract val destinationDir: DirectoryProperty


    /**
     * The name of the packaged chart file.
     */
    @get:Internal("Represented as part of packageFile")
    val chartFileName: Provider<String> =
        packagedChartFileName(chartName, chartVersion)


    /**
     * The full path of the packaged chart file (read-only property).
     */
    @get:OutputFile
    val packageFile: Provider<RegularFile> =
        destinationDir.file(chartFileName)


    /**
     * The full path of the packaged chart file (read-only property).
     *
     * @deprecated use [packageFile] instead
     */
    @Suppress("unused")
    @Deprecated(message = "use packageFile", replaceWith = ReplaceWith("packageFile"))
    @get:Internal("replaced by packageFile property")
    val chartOutputPath: Provider<RegularFile>
        get() = packageFile


    init {
        chartName.convention(chartDescriptor.map {
            requireNotNull(it.name) { "Chart name must either be present in Chart.yaml, or specified explicitly" }
        })

        chartVersion.convention(chartDescriptor.map {
            requireNotNull(it.version) { "Chart version must either be present in Chart.yaml, or specified explicitly" }
        })

        destinationDir.convention(project.layout.buildDirectory.dir("helm/charts"))
    }


    @TaskAction
    fun helmPackage() {

        // Make sure the destination directory exists, otherwise helm package will fail.
        this.destinationDir.get().asFile.mkdirs()

        execHelm("package") {
            option("--app-version", appVersion)
            flag("--dependency-update", updateDependencies)
            option("--destination", destinationDir)
            option("--version", chartVersion)
            args(sourceDir)
        }
    }
}
