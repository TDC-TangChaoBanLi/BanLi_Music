package com.banlimusic.app.core.model

import kotlin.math.abs

/**
 * The four app-level playback quality tiers. Every provider is normalized to
 * these tiers: providers with fewer choices map several tiers onto the closest
 * available quality, providers with more choices discard everything outside the
 * closest value for each preset.
 */
object QualityTiers {
    val PRESET_KBPS: List<Int> = listOf(128, 192, 320, 740)

    /** Returns the preset tier closest to [kbps]. */
    fun normalize(kbps: Int): Int = PRESET_KBPS.minByOrNull { abs(it - kbps) } ?: 128

    /** True when [kbps] is one of the four presets. */
    fun isPreset(kbps: Int): Boolean = kbps in PRESET_KBPS

    /**
     * Maps an app-level tier to a provider-specific quality.
     *
     * - An empty provider list means the provider does not expose quality
     *   choices; the normalized tier is passed through.
     * - A provider with fewer than four choices maps multiple tiers to the same
     *   closest quality.
     * - A provider with more than four choices extracts the closest value for
     *   each preset and discards the rest.
     */
    fun mapToProvider(requestedKbps: Int, providerQualities: Collection<Int>): Int {
        val target = normalize(requestedKbps)
        val available = providerQualities.filter { it > 0 }
        if (available.isEmpty()) return target
        return available.minByOrNull { abs(it - target) } ?: target
    }

    /**
     * Projects a provider quality list onto the app's presets, preserving the
     * preset order. Used by UIs that show a stable four-tier selector.
     */
    fun projectToPresets(providerQualities: Collection<Int>): List<Int> {
        val available = providerQualities.filter { it > 0 }
        if (available.isEmpty()) return PRESET_KBPS
        return PRESET_KBPS.map { preset -> mapToProvider(preset, available) }
    }
}
