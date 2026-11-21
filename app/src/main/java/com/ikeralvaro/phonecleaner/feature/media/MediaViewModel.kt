package com.ikeralvaro.phonecleaner.feature.media

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaPhase { IDLE, NO_ACCESS, ANALYZING, READY }

@Immutable
data class MediaUiState(
    val phase: MediaPhase = MediaPhase.IDLE,
    val progress: Float = 0f,
    val duplicates: List<MediaGroup> = emptyList(),
    val similar: List<MediaGroup> = emptyList(),
    val screenshots: List<MediaItem> = emptyList(),
    val largeVideos: List<MediaItem> = emptyList(),
    val largeFiles: List<MediaItem> = emptyList(),
    val lastDeletedBytes: Long? = null,
) {
    fun groups(category: MediaCategory): List<MediaGroup>? = when (category) {
        MediaCategory.DUPLICATES -> duplicates
        MediaCategory.SIMILAR -> similar
        else -> null
    }

    fun items(category: MediaCategory): List<MediaItem> = when (category) {
        MediaCategory.DUPLICATES -> duplicates.flatMap { it.items }
        MediaCategory.SIMILAR -> similar.flatMap { it.items }
        MediaCategory.SCREENSHOTS -> screenshots
        MediaCategory.LARGE_VIDEOS -> largeVideos
        MediaCategory.LARGE_FILES -> largeFiles
    }

    fun count(category: MediaCategory): Int = groups(category)?.size ?: items(category).size

    fun bytes(category: MediaCategory): Long =
        groups(category)?.sumOf { it.removableBytes } ?: items(category).sumOf { it.sizeBytes }

    /** Selección inicial: en grupos, todo menos la foto a conservar; en listas, nada (se revisa a mano). */
    fun defaultSelection(category: MediaCategory): Set<Long> =
        groups(category)?.flatMap { g -> g.items.filter { it.id != g.keeperId }.map { it.id } }?.toSet() ?: emptySet()
}

class MediaViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.mediaRepository
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun analyzeIfNeeded() {
        val phase = _state.value.phase
        if (phase == MediaPhase.IDLE || phase == MediaPhase.NO_ACCESS) analyze()
    }

    fun analyze() {
        if (job?.isActive == true) return
        if (!repo.hasAccess()) {
            _state.value = MediaUiState(phase = MediaPhase.NO_ACCESS)
            return
        }
        _state.update { it.copy(phase = MediaPhase.ANALYZING, progress = 0f) }
        job = viewModelScope.launch {
            val images = repo.images()
            val videos = repo.videos()
            val large = repo.largeFiles(LARGE_FILE_BYTES)
            _state.update { it.copy(progress = 0.1f) }

            val result = withContext(Dispatchers.Default) {
                val screenshots = images.filter(DuplicateFinder::isScreenshot)
                val exact = DuplicateFinder.exactDuplicates(images) { repo.md5(it) }
                val removableExact = exact.flatMap { g -> g.items.filter { it.id != g.keeperId }.map { it.id } }.toSet()
                val candidates = images
                    .filter { !DuplicateFinder.isScreenshot(it) && it.id !in removableExact && it.mimeType != "image/gif" }
                    .take(MAX_SIMILAR_CANDIDATES)
                // Miniaturas y hashes en paralelo (4 a la vez): mucho más rápido en galerías grandes.
                val hashes = java.util.concurrent.ConcurrentHashMap<Long, Long>()
                val done = java.util.concurrent.atomic.AtomicInteger()
                val total = candidates.size.coerceAtLeast(1)
                val pool = Dispatchers.IO.limitedParallelism(4)
                coroutineScope {
                    candidates.map { item ->
                        async(pool) {
                            repo.perceptualHash(item)?.let { hashes[item.id] = it }
                            val n = done.incrementAndGet()
                            if (n % 25 == 0) _state.update { it.copy(progress = 0.1f + 0.9f * n / total) }
                        }
                    }.awaitAll()
                }
                MediaUiState(
                    phase = MediaPhase.READY,
                    progress = 1f,
                    duplicates = exact,
                    similar = DuplicateFinder.similarGroups(candidates, hashes),
                    screenshots = screenshots.sortedByDescending { it.sizeBytes },
                    largeVideos = videos.filter { it.sizeBytes >= LARGE_VIDEO_BYTES }.sortedByDescending { it.sizeBytes },
                    largeFiles = large,
                )
            }
            _state.value = result
        }
    }

    suspend fun prepareDelete(ids: Set<Long>): DeleteRequest {
        val all = MediaCategory.entries.flatMap { _state.value.items(it) }.distinctBy { it.id }
        return repo.prepareDelete(all.filter { it.id in ids })
    }

    /** Quita los elementos borrados de todas las listas y recalcula los grupos. */
    fun onDeleted(ids: Set<Long>, permanentlyFreedBytes: Long) {
        if (ids.isEmpty()) return
        _state.update { s ->
            val deletedBytes = MediaCategory.entries.flatMap { s.items(it) }.distinctBy { it.id }
                .filter { it.id in ids }.sumOf { it.sizeBytes }
            s.copy(
                duplicates = s.duplicates.prune(ids),
                similar = s.similar.prune(ids),
                screenshots = s.screenshots.filter { it.id !in ids },
                largeVideos = s.largeVideos.filter { it.id !in ids },
                largeFiles = s.largeFiles.filter { it.id !in ids },
                lastDeletedBytes = deletedBytes,
            )
        }
        if (permanentlyFreedBytes > 0) {
            viewModelScope.launch { container.settingsRepository.recordClean(permanentlyFreedBytes) }
        }
    }

    fun consumeDeletedMessage() = _state.update { it.copy(lastDeletedBytes = null) }

    private fun List<MediaGroup>.prune(ids: Set<Long>): List<MediaGroup> = mapNotNull { group ->
        val remaining = group.items.filter { it.id !in ids }
        if (remaining.size < 2) null else MediaGroup(remaining, DuplicateFinder.chooseKeeper(remaining).id)
    }

    companion object {
        const val LARGE_FILE_BYTES = 100_000_000L
        const val LARGE_VIDEO_BYTES = 50_000_000L
        const val MAX_SIMILAR_CANDIDATES = 4_000
    }
}
