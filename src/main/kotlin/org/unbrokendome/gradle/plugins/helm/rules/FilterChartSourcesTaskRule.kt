package org.unbrokendome.gradle.plugins.helm.rules

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.filtering
import org.unbrokendome.gradle.plugins.helm.dsl.HelmChart
import org.unbrokendome.gradle.plugins.helm.dsl.HelmChartInternal
import org.unbrokendome.gradle.plugins.helm.dsl.setParent
import org.unbrokendome.gradle.plugins.helm.tasks.HelmFilterSources


private val namePattern = RuleNamePattern.parse("helmFilter<Chart>ChartSources")


/**
 * Gets the name of the [HelmFilterSources] task for this chart.
 */
val HelmChart.filterSourcesTaskName
    get() = namePattern.mapName(name)


/**
 * A rule that creates a [HelmFilterSources] task for each chart.
 */
internal class FilterChartSourcesTaskRule(
    tasks: TaskContainer,
    charts: NamedDomainObjectCollection<HelmChart>
) : AbstractHelmChartTaskRule<HelmFilterSources>(
    HelmFilterSources::class.java, tasks, charts, namePattern
) {
    override fun HelmFilterSources.configureFrom(chart: HelmChart) {
        description = "Filters the sources for the ${chart.name} chart."
        configuredChartName.set(chart.name)
        chartName.set(chart.chartName)
        chartVersion.set(chart.chartVersion)
        sourceDir.set(chart.sourceDir)
        targetDir.set((chart as HelmChartInternal).filteredSourcesDir)

        filtering.setParent(chart.filtering)
    }
}
