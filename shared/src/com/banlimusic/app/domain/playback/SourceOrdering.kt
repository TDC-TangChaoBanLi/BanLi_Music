package com.banlimusic.app.domain.playback

import com.banlimusic.app.core.model.Track
import com.banlimusic.app.core.model.TrackAction
import com.banlimusic.app.core.model.TrackSource

/**
 * Pure ordering helpers shared by the player and the media source. The merged
 * track keeps every provider source; a user preference only changes which one
 * is tried first (and which one is shown as the primary badge).
 */
object SourceOrdering {

    /** Moves [preferredProviderId] to the front, keeping every other source in order. */
    fun reorderSources(
        sources: List<TrackSource>,
        preferredProviderId: String?,
    ): List<TrackSource> {
        if (sources.size < 2 || preferredProviderId.isNullOrBlank()) return sources
        val index = sources.indexOfFirst { it.providerId == preferredProviderId }
        if (index <= 0) return sources
        return listOf(sources[index]) + sources.filterIndexed { i, _ -> i != index }
    }

    /** Streamable sources of a track, preferred first, then rotated by [rotate]. */
    fun streamCandidates(
        track: Track,
        preferredProviderId: String? = null,
        rotate: Int = 0,
    ): List<StreamCandidate> {
        val ordered = reorderSources(
            sources = track.sources.filter { TrackAction.STREAM in it.availableActions },
            preferredProviderId = preferredProviderId,
        ).map { StreamCandidate(it.providerId, it.providerTrackId) }
        return rotate(ordered, rotate)
    }

    /**
     * Rotates the candidate list so retries use a different source. A shift of
     * zero is the common case and keeps the original list instance.
     */
    fun <T> rotate(candidates: List<T>, shift: Int): List<T> {
        if (candidates.size < 2 || shift == 0) return candidates
        val start = ((shift % candidates.size) + candidates.size) % candidates.size
        if (start == 0) return candidates
        return candidates.drop(start) + candidates.take(start)
    }
}
