package com.ikeralvaro.phonecleaner.feature.compress

import androidx.compose.runtime.Immutable
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.feature.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CompressTab { PHOTOS, VIDEOS }
enum class RunState { IDLE, RUNNING, FINISHED, FAILED }

data class CompressCandidate(val item: MediaItem, val estimatedBytes: Long) {
    val savingBytes: Long get() = (item.sizeBytes - estimatedBytes).coerceAtLeast(0L)
}

data class CompressRun(
    val state: RunState = RunState.IDLE,
    val done: Int = 0,
    val total: Int = 0,
    val savedBytes: Long = 0L,
    val compressedCount: Int = 0,
    val originals: List<String> = emptyList(),
    val originalsTrashed: Boolean = false,
    /** Registro de originales de esta ejecución (uno por trabajo). */
    val originalsFile: String? = null,
)

@Immutable
data class CompressUiState(
    val loading: Boolean = true,
    val hasAccess: Boolean = true,
    val tab: CompressTab = CompressTab.PHOTOS,
    val mode: CompressionMode = CompressionMode.HIGH_QUALITY,
    val photos: List<CompressCandidate> = emptyList(),
    val videos: List<CompressCandidate> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val run: CompressRun = CompressRun(),
    /** La compresión acaba de terminar con la pantalla abierta: se ofrece mover los originales a la papelera. */
    val justFinished: Boolean = false,
) {
    val visible: List<CompressCandidate> get() = if (tab == CompressTab.PHOTOS) photos else videos
    // Se calculan una vez por estado, no en cada recomposición.
    val selectedCandidates: List<CompressCandidate> =
        if (selected.isEmpty()) emptyList() else (photos + videos).filter { it.item.id in selected }
    val estimatedSavings: Long = selectedCandidates.sumOf { it.savingBytes }
}

class CompressViewModel(private val container: AppContainer) : ViewModel() {
    private val workManager = WorkManager.getInstance(container.appContext)
    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state.asStateFlow()
    private var images: List<MediaItem> = emptyList()
    private var videos: List<MediaItem> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(CompressWorker.UNIQUE_NAME).collect { infos ->
                val info = infos.lastOrNull() ?: return@collect
                val run = toRun(info)
                // start() ya marcó RUNNING: un estado antiguo (FINISHED de una ejecución anterior) no lo pisa.
                if (startingWorkId != null && info.id != startingWorkId) return@collect
                startingWorkId = null
                val finishedNow = run.state == RunState.FINISHED && _state.value.run.state == RunState.RUNNING
                _state.update {
                    it.copy(
                        run = run.copy(originalsTrashed = it.run.originalsTrashed && run.state != RunState.RUNNING),
                        justFinished = it.justFinished || finishedNow,
                    )
                }
                if (finishedNow) load()
            }
        }
    }

    /** Una sola carga a la vez. Solo se piden a MediaStore los JPEG y vídeos con tamaño mínimo. */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            if (!container.permissionManager.hasMediaAccess()) {
                _state.update { it.copy(loading = false, hasAccess = false) }
                return@launch
            }
            images = container.mediaRepository.jpegImages(CompressionPlanner.MIN_PHOTO_BYTES)
            videos = container.mediaRepository.videos(CompressionPlanner.MIN_VIDEO_BYTES)
            recompute(selectAllPhotos = _state.value.loading)
        }
    }

    fun setTab(tab: CompressTab) = _state.update { it.copy(tab = tab) }

    fun setMode(mode: CompressionMode) {
        _state.update { it.copy(mode = mode) }
        viewModelScope.launch { recompute(selectAllPhotos = false) }
    }

    fun toggle(id: Long) = _state.update { s -> s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id) }

    fun selectAllVisible(select: Boolean) = _state.update { s ->
        val ids = s.visible.map { it.item.id }.toSet()
        s.copy(selected = if (select) s.selected + ids else s.selected - ids)
    }

    private var startingWorkId: java.util.UUID? = null

    fun start() {
        val s = _state.value
        val items = s.selectedCandidates.map { it.item }
        if (items.isEmpty() || s.run.state == RunState.RUNNING) return
        // Se marca RUNNING ya (en el hilo principal): un segundo toque no lanza otro trabajo.
        _state.update { it.copy(run = CompressRun(RunState.RUNNING, 0, items.size), selected = emptySet(), justFinished = false) }
        val request = OneTimeWorkRequestBuilder<CompressWorker>()
        val workId = java.util.UUID.randomUUID()
        startingWorkId = workId
        val jobFile = File(container.appContext.filesDir, "compress/job-$workId.txt")
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CompressJobs.write(jobFile, items) }
            val work = request
                .setId(workId)
                .setInputData(
                    workDataOf(
                        CompressWorker.KEY_JOB_FILE to jobFile.path,
                        CompressWorker.KEY_MODE to s.mode.name,
                        CompressWorker.KEY_TOTAL to items.size,
                    ),
                )
                .build()
            workManager.enqueueUniqueWork(CompressWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, work)
        }
    }

    /** Android 11+: petición del sistema para mover los originales comprimidos a la papelera. */
    fun trashOriginalsRequest(): IntentSender? {
        val originals = _state.value.run.originals
        if (Build.VERSION.SDK_INT < 30 || originals.isEmpty()) return null
        return try {
            MediaStore.createTrashRequest(container.appContext.contentResolver, originals.map(Uri::parse), true).intentSender
        } catch (_: Exception) {
            null
        }
    }

    fun consumeJustFinished() = _state.update { it.copy(justFinished = false) }

    fun onOriginalsTrashed(ok: Boolean) {
        if (!ok) return
        deleteOriginalsFile()
        _state.update { it.copy(run = it.run.copy(originalsTrashed = true)) }
        load()
    }

    fun dismissResult() {
        deleteOriginalsFile()
        workManager.pruneWork()
        _state.update { it.copy(run = CompressRun(), justFinished = false) }
    }

    private fun deleteOriginalsFile() {
        val path = _state.value.run.originalsFile ?: return
        viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
    }

    private suspend fun recompute(selectAllPhotos: Boolean) {
        val mode = _state.value.mode
        val (photos, vids) = withContext(Dispatchers.Default) {
            val photos = images
                .filter { CompressionPlanner.isPhotoCandidate(it.mimeType, it.sizeBytes, it.width, it.height, mode) }
                .map { CompressCandidate(it, CompressionPlanner.estimatePhotoBytes(it.width, it.height, it.sizeBytes, mode)) }
                .sortedByDescending { it.savingBytes }
            val hevc = container.videoCompressor.supportsHevc
            val vids = videos
                .filter { it.sizeBytes >= CompressionPlanner.MIN_VIDEO_BYTES && it.durationMs > 0 }
                .mapNotNull { v ->
                    val bitrate = CompressionPlanner.bitrateFromSize(v.sizeBytes, v.durationMs)
                    CompressionPlanner.videoTargetBitrate(v.width, v.height, 30f, bitrate, hevc, mode)
                        ?.let { CompressCandidate(v, CompressionPlanner.estimateVideoBytes(v.durationMs, it)) }
                }
                .sortedByDescending { it.savingBytes }
            photos to vids
        }
        _state.update { s ->
            val available = (photos + vids).map { it.item.id }.toSet()
            val selected = if (selectAllPhotos) photos.map { it.item.id }.toSet() else s.selected intersect available
            s.copy(loading = false, hasAccess = true, photos = photos, videos = vids, selected = selected)
        }
    }

    private suspend fun toRun(info: WorkInfo): CompressRun = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> CompressRun(
            state = RunState.RUNNING,
            done = info.progress.getInt(CompressWorker.KEY_DONE, 0),
            total = info.progress.getInt(CompressWorker.KEY_TOTAL, _state.value.run.total),
            savedBytes = info.progress.getLong(CompressWorker.KEY_SAVED, 0L),
        )
        WorkInfo.State.SUCCEEDED -> finishedRun(info.outputData)
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> CompressRun(state = RunState.FAILED)
    }

    /** Lee el registro de originales en IO (no en el hilo principal). */
    private suspend fun finishedRun(data: Data): CompressRun {
        val path = data.getString(CompressWorker.KEY_ORIGINALS_FILE)
        val originals = withContext(Dispatchers.IO) {
            path?.let { CompressJobs.readOriginals(File(it)).map { o -> o.first } }.orEmpty()
        }
        return CompressRun(
            state = RunState.FINISHED,
            done = data.getInt(CompressWorker.KEY_TOTAL, 0),
            total = data.getInt(CompressWorker.KEY_TOTAL, 0),
            savedBytes = data.getLong(CompressWorker.KEY_SAVED, 0L),
            compressedCount = data.getInt(CompressWorker.KEY_COMPRESSED, 0),
            originals = originals,
            originalsFile = path,
        )
    }
}
