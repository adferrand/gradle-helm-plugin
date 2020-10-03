package org.unbrokendome.gradle.plugins.helm.command.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.unbrokendome.gradle.plugins.helm.command.helmCommandSupport
import org.unbrokendome.gradle.plugins.helm.model.ReleaseStatus


/**
 * Installs a chart into a remote Kubernetes cluster as a new release, or upgrades an existing release.
 *
 * This task will call `helm upgrade --install` by default, or `helm install --replace` if the release does
 * not exist or has previously failed.
 */
@Suppress("LeakingThis")
abstract class HelmInstallOrUpgrade : AbstractHelmInstallationCommandTask() {

    /**
     * If `true`, re-use the given release name, even if that name is already used.
     *
     * If this is `true`, the task will perform a `helm install --replace` command. If it is `false` (default), then
     * it will perform a `helm upgrade --install` command instead.
     */
    @get:Internal
    abstract val replace: Property<Boolean>


    /**
     * If `true`, reset the values to the ones built into the chart when upgrading.
     *
     * Corresponds to the `--reset-values` parameter of the `helm upgrade` CLI command.
     *
     * If [replace] is set to `true`, this property will be ignored.
     */
    @get:Internal
    abstract val resetValues: Property<Boolean>


    /**
     * If `true`, reuse the last release's values, and merge in any new values. If [resetValues] is specified,
     * this is ignored.

     * Corresponds to the `--reuse-values` parameter of the `helm upgrade` CLI command.
     *
     * If [replace] is set to `true`, this property will be ignored.
     */
    @get:Internal
    abstract val reuseValues: Property<Boolean>


    init {
        replace.convention(false)
    }


    @TaskAction
    fun installOrUpgrade() {

        if (shouldUseInstallReplace()) {
            execHelm("install") {
                args(releaseName)
                args(chart)
                flag("--replace")
            }

        } else {
            execHelm("upgrade") {
                args(releaseName)
                args(chart)
                flag("--install")
                flag("--reset-values", resetValues)
                flag("--reuse-values", reuseValues)
            }
        }
    }


    private fun shouldUseInstallReplace(): Boolean {
        if (replace.get()) {
            return true
        }

        val release = helmCommandSupport.getRelease(releaseName)

        if (release == null) {
            logger.info("Release \"{}\" does not exist. Using 'helm upgrade --install' to install it.")
            return false
        }

        if (release.status == ReleaseStatus.FAILED) {
            logger.info("Release \"{}\" has previously failed. Using 'helm install --replace' to install it.")
            return true
        }

        return false
    }
}
