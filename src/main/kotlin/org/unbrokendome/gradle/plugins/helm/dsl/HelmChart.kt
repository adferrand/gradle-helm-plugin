package org.unbrokendome.gradle.plugins.helm.dsl

import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.unbrokendome.gradle.plugins.helm.HELM_MAIN_CHART_NAME
import org.unbrokendome.gradle.plugins.helm.command.tasks.HelmPackage
import org.unbrokendome.gradle.plugins.helm.model.ChartDescriptor
import org.unbrokendome.gradle.plugins.helm.model.ChartDescriptorYaml
import org.unbrokendome.gradle.plugins.helm.model.ChartModelDependencies
import org.unbrokendome.gradle.plugins.helm.model.ChartRequirementsYaml
import org.unbrokendome.gradle.plugins.helm.rules.packageTaskName
import org.unbrokendome.gradle.plugins.helm.util.versionProvider
import javax.inject.Inject


/**
 * Represents a Helm chart built by this project.
 */
interface HelmChart : Named, Buildable {

    /**
     * The chart name.
     *
     * By default, the chart will have the same name as in the Gradle DSL (except for the "main" chart which
     * will have the same name as the project by default).
     */
    val chartName: Property<String>


    /**
     * The chart version.
     *
     * By default, the chart will have the same version as the Gradle project.
     */
    val chartVersion: Property<String>


    /**
     * The directory that contains the chart sources.
     */
    val sourceDir: DirectoryProperty


    /**
     * The base output directory. When the chart is built, a subdirectory with the same name as the chart
     * will be created below this. (That subdirectory can be queried using [outputDir].)
     *
     * By default, this is the same as the [HelmExtension.outputDir] configured on the global `helm` extension.
     *
     * @see outputDir
     */
    val baseOutputDir: DirectoryProperty


    /**
     * The directory where the (exploded) chart files will be written.
     *
     * This is read-only (a [Provider], not a [Property]) because Helm demands that the chart directory has
     * the same name as the chart. To change the base output directory, set the [baseOutputDir] property to
     * a different value.
     *
     * @see baseOutputDir
     */
    @JvmDefault
    val outputDir: Provider<Directory>
        get() = baseOutputDir.flatMap { it.dir(chartName) }


    /**
     * The name of the packaged chart file.
     */
    @JvmDefault
    val packageFileName: Provider<String>
        get() = HelmPackage.packagedChartFileName(chartName, chartVersion)


    /**
     * The location of the packaged chart file.
     *
     * The file will be placed inside the [baseOutputDir] and have a name according to the pattern
     * `<chart>-<version>.tgz`.
     */
    val packageFile: Provider<RegularFile>


    /**
     * The location of the packaged chart file.
     *
     * The file will be placed inside the [baseOutputDir] and have a name according to the pattern
     * `<chart>-<version>.tgz`.
     *
     * @deprecated use [packageFile] instead
     */
    @JvmDefault
    @Deprecated(message = "use packageFile", replaceWith = ReplaceWith("packageFile"))
    val packageOutputFile: Provider<RegularFile>
        get() = packageFile


    /**
     * A [CopySpec] that allows copying additional files into the chart.
     */
    val extraFiles: CopySpec


    /**
     * Configures a [CopySpec] that allows copying additional files into the chart.
     *
     * @param action an [Action] to configure on the [extraFiles] `CopySpec`
     */
    fun extraFiles(action: Action<CopySpec>) {
        action.execute(extraFiles)
    }
}


internal interface HelmChartInternal : HelmChart {

    /**
     * The directory where the filtered sources of the chart will be placed.
     */
    val filteredSourcesDir: Provider<Directory>

    /**
     * The directory where the resolved dependencies (subcharts) of the chart will be placed.
     */
    val dependenciesDir: Provider<Directory>

    /**
     * The chart descriptor, as parsed from the Chart.yaml file.
     */
    val chartDescriptor: Provider<ChartDescriptor>

    /**
     * The dependencies, as declared in either the Chart.yaml file (for API version v2) or the
     * requirements.yaml file (for API version v1).
     *
     * If the API version is v1 and no requirements.yaml file exists, the provider will produce
     * an empty [ChartModelDependencies].
     */
    val modelDependencies: Provider<ChartModelDependencies>
}


@Suppress("LeakingThis")
private abstract class DefaultHelmChart
@Inject constructor(
    private val name: String,
    project: Project,
    baseOutputDir: Provider<Directory>,
    filteredSourcesBaseDir: Provider<Directory>,
    dependenciesBaseDir: Provider<Directory>
) : HelmChart, HelmChartInternal {

    private val tasks = project.tasks


    final override fun getName(): String =
        name


    final override val packageFile: Provider<RegularFile>
        get() = tasks.named(packageTaskName, HelmPackage::class.java).flatMap { it.packageFile }


    final override fun getBuildDependencies(): TaskDependency =
        TaskDependency { task ->
            if (task != null) {
                setOf(
                    task.project.tasks.getByName(packageTaskName)
                )
            } else {
                emptySet()
            }
        }


    final override val filteredSourcesDir: Provider<Directory> =
        filteredSourcesBaseDir.flatMap { it.dir(chartName) }


    final override val dependenciesDir: Provider<Directory> =
        dependenciesBaseDir.flatMap { it.dir(chartName) }


    final override val chartDescriptor: Provider<ChartDescriptor> =
        ChartDescriptorYaml.loading(sourceDir.file("Chart.yaml"))


    final override val modelDependencies: Provider<ChartModelDependencies> =
        chartDescriptor.flatMap { descriptor ->
            if (descriptor.apiVersion == "v1") {
                ChartRequirementsYaml.loading(sourceDir.file("requirements.yaml"))
            } else {
                chartDescriptor
            }
        }


    override val extraFiles: CopySpec = project.copySpec()


    init {
        this.baseOutputDir.convention(baseOutputDir)
        chartName.convention(name)
        chartVersion.convention(project.versionProvider)
    }
}


/**
 * Creates a [NamedDomainObjectContainer] that holds [HelmChart]s.
 *
 * @receiver the Gradle [Project]
 * @return the container for `HelmChart`s
 */
internal fun Project.helmChartContainer(
    baseOutputDir: Provider<Directory>,
    filteredSourcesBaseDir: Provider<Directory>,
    dependenciesBaseDir: Provider<Directory>
): NamedDomainObjectContainer<HelmChart> =
    container(HelmChart::class.java) { name ->
        objects.newInstance<HelmChart>(
            DefaultHelmChart::class.java, name,
            this, baseOutputDir, filteredSourcesBaseDir, dependenciesBaseDir
        ).also { chart ->
            // The "main" chart should be named like the project by default
            if (name == HELM_MAIN_CHART_NAME) {
                chart.chartName.convention(this@helmChartContainer.name)
            }
        }
    }
