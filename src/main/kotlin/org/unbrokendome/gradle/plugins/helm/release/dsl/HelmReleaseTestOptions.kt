package org.unbrokendome.gradle.plugins.helm.release.dsl

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.unbrokendome.gradle.plugins.helm.util.withDefault
import java.time.Duration

/**
 * Remote testing options for a release using `helm test`.
 */
interface HelmReleaseTestOptions {

    /**
     * Whether testing is enabled for this release (default `true`).
     */
    val enabled: Provider<Boolean>

    /**
     * If `true`, dump the logs from test pods (this runs after all tests are complete, but before any cleanup).
     *
     * Corresponds to the `--logs` CLI option for the `helm test` command.
     */
    val showLogs: Provider<Boolean>


    /**
     * The timeout to use when testing this release using `helm test`. If not set, defaults to the
     * same value as [HelmRelease.remoteTimeout].
     *
     * Corresponds to the `--timeout` CLI option for the `helm test` command.
     */
    val timeout: Provider<Duration>
}



/**
 * Configures remote testing for a release using `helm test`.
 */
interface ConfigurableHelmReleaseTestOptions : HelmReleaseTestOptions {

    override val enabled: Property<Boolean>

    override val showLogs: Property<Boolean>

    override val timeout: Property<Duration>
}


internal fun ConfigurableHelmReleaseTestOptions.setFrom(other: HelmReleaseTestOptions) {
    enabled.set(other.enabled)
    showLogs.set(other.showLogs)
    timeout.set(other.timeout)
}


internal fun HelmReleaseTestOptions.withDefaults(
    defaults: HelmReleaseTestOptions, providers: ProviderFactory
): HelmReleaseTestOptions =
    object : HelmReleaseTestOptions {

        override val enabled: Provider<Boolean> =
            this@withDefaults.enabled.withDefault(defaults.enabled, providers)

        override val showLogs: Provider<Boolean> =
            this@withDefaults.showLogs.withDefault(defaults.showLogs, providers)

        override val timeout: Provider<Duration> =
            this@withDefaults.timeout.withDefault(defaults.timeout, providers)
    }
