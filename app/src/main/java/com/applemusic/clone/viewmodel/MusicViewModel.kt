package com.applemusic.clone.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.applemusic.clone.data.AudioRepository
import com.applemusic.clone.data.LyricsParser
import com.applemusic.clone.data.OnlineMetadataManager
import com.applemusic.clone.model.AudioItem
import com.applemusic.clone.model.LrcLine
import com.applemusic.clone.model.Playlist
import com.applemusic.clone.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    private val prefs = application.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

    // ── 歌曲列表 ──────────────────────────────────────────
    private val _songs = MutableStateFlow<List<AudioItem>>(emptyList())
    val songs: StateFlow<List<AudioItem>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── 播放状态 ──────────────────────────────────────────
    private val _currentSong = MutableStateFlow<AudioItem?>(null)
    val currentSong: StateFlow<AudioItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isShuffleOn = MutableStateFlow(false)
    val isShuffleOn: StateFlow<Boolean> = _isShuffleOn.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // ── 歌词 ──────────────────────────────────────────────
    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    // ── 播放队列 (Queue) ──────────────────────────────────
    private val _queue = MutableStateFlow<List<AudioItem>>(emptyList())
    val queue: StateFlow<List<AudioItem>> = _queue.asStateFlow()

    // ── 播放列表 (Playlists) ──────────────────────────────
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // ── 睡眠定时器 (Sleep Timer) ──────────────────────────
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: Job? = null


    // ── 搜索 ──────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── 收藏 ──────────────────────────────────────────────
    private val _favoriteIds = MutableStateFlow<Set<Long>>(loadFavoritesFromPrefs())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // ── 最近播放 ──────────────────────────────────────────
    private val _recentlyPlayed = MutableStateFlow<List<AudioItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<AudioItem>> = _recentlyPlayed.asStateFlow()

    // ── MediaController ───────────────────────────────────
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null

    /** Controller 尚未就绪时延后执行的播放动作（避免点击无效、旧队列残留） */
    private var pendingPlayAction: (() -> Unit)? = null

    init {
        loadSongs()
        loadPlaylistsFromPrefs()
        connectToPlaybackService()
    }

    // ── 加载歌曲 ──────────────────────────────────────────
    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            _songs.value = repository.getLocalAudioFiles()
            _isLoading.value = false
        }
    }

    // ── 连接播放服务 ──────────────────────────────────────
    private fun connectToPlaybackService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            controller?.let { c ->
                _isPlaying.value = c.isPlaying
                _isShuffleOn.value = c.shuffleModeEnabled
                _repeatMode.value = c.repeatMode
            }
            startProgressTracking()
            pendingPlayAction?.let { run ->
                pendingPlayAction = null
                run()
            }
        }, MoreExecutors.directExecutor())
    }

    // ── Player 事件监听 ───────────────────────────────────
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) startProgressTracking() else progressJob?.cancel()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            val song = _songs.value.find { it.id == id }
            _currentSong.value = song
            song?.let { addToRecentlyPlayed(it) }
            loadLyricsForCurrent()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleOn.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncQueueFromController()
        }
    }

    private fun syncQueueFromController() {
        val c = controller ?: return
        val currentSongs = _songs.value
        val newQueue = mutableListOf<AudioItem>()
        for (i in 0 until c.mediaItemCount) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
            currentSongs.find { it.id == id }?.let { newQueue.add(it) }
        }
        _queue.value = newQueue
    }

    // ── 进度跟踪 ──────────────────────────────────────────
    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _currentPositionMs.value = controller?.currentPosition ?: 0L
                updateCurrentLyricIndex()
                delay(500)
            }
        }
    }

    // ── 歌词同步 ──────────────────────────────────────────
    private fun loadLyricsForCurrent() {
        viewModelScope.launch {
            val current = _currentSong.value ?: return@launch
            val path = current.lyricsPath
            _lyrics.value = if (path != null) LyricsParser.parse(path) else emptyList()
            _currentLyricIndex.value = -1
        }
    }

    private fun updateCurrentLyricIndex() {
        val lrcList = _lyrics.value
        if (lrcList.isEmpty()) return
        val pos = _currentPositionMs.value
        var idx = lrcList.indexOfLast { it.timeMs <= pos }
        if (idx < 0) idx = 0
        if (idx != _currentLyricIndex.value) {
            _currentLyricIndex.value = idx
        }
    }

    // ── 播放控制 ──────────────────────────────────────────

    /**
     * 以指定列表为上下文播放，从 startIndex 开始。
     * 这是专辑 / 艺术家 / 歌曲界面「播放」的标准入口。
     *
     * @param preserveShuffleMode 为 false 时会关闭随机播放，使「下一首」与列表顺序一致。
     *        专辑/艺术家页的「随机播放」应使用 [playShuffledList]。
     */
    fun playList(songs: List<AudioItem>, startIndex: Int = 0, preserveShuffleMode: Boolean = false) {
        val c = controller
        if (c == null) {
            pendingPlayAction = { playList(songs, startIndex, preserveShuffleMode) }
            return
        }
        if (songs.isEmpty()) return
        val clampedIdx = startIndex.coerceIn(0, songs.lastIndex)

        val mediaItems = songs.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.albumArtUri)
                        .build()
                )
                .build()
        }

        if (!preserveShuffleMode) {
            c.shuffleModeEnabled = false
            _isShuffleOn.value = false
        }
        c.stop()
        c.setMediaItems(mediaItems, clampedIdx, 0L)
        c.prepare()
        c.play()
        syncQueueFromController()
        val startSong = songs[clampedIdx]
        _currentSong.value = startSong
        addToRecentlyPlayed(startSong)
        loadLyricsForCurrent()
    }

    /**
     * 随机播放指定列表：开启 shuffle 并从随机曲目开始，整段队列仅为该列表。
     */
    fun playShuffledList(songs: List<AudioItem>) {
        val c = controller
        if (c == null) {
            pendingPlayAction = { playShuffledList(songs) }
            return
        }
        if (songs.isEmpty()) return
        val clampedIdx = songs.indices.random()

        val mediaItems = songs.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.albumArtUri)
                        .build()
                )
                .build()
        }

        c.shuffleModeEnabled = true
        _isShuffleOn.value = true
        c.stop()
        c.setMediaItems(mediaItems, clampedIdx, 0L)
        c.prepare()
        c.play()
        syncQueueFromController()
        val startSong = songs[clampedIdx]
        _currentSong.value = startSong
        addToRecentlyPlayed(startSong)
        loadLyricsForCurrent()
    }

    /**
     * 兼容旧调用：以完整歌曲库为上下文播放指定歌曲。
     */
    fun play(song: AudioItem) {
        val songs = _songs.value
        val idx = songs.indexOfFirst { it.id == song.id }
        if (idx < 0) return
        playList(songs, idx)
    }

    fun playNext(song: AudioItem) {
        val c = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()
        
        // 插播：如果当前没有歌曲，或者完全没有加载，直接调用 play(song)
        if (c.mediaItemCount == 0) {
            play(song)
            return
        }
        
        // 插入到当前歌曲的下一首
        val insertIndex = c.currentMediaItemIndex + 1
        c.addMediaItem(insertIndex, mediaItem)
        // syncQueueFromController 会由 onTimelineChanged 自动触发
    }

    fun addToQueue(song: AudioItem) {
        val c = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()
        
        if (c.mediaItemCount == 0) {
            play(song)
        } else {
            c.addMediaItem(c.mediaItemCount, mediaItem)
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() { controller?.seekToNextMediaItem() }

    /**
     * 跳到播放队列中指定位置的歌曲（不清空队列）
     */
    fun skipToQueueIndex(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun skipPrev() {
        val c = controller ?: return
        if ((c.currentPosition) > 3000) {
            c.seekTo(0)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun toggleShuffle() {
        val c = controller ?: return
        val newVal = !c.shuffleModeEnabled
        c.shuffleModeEnabled = newVal
        _isShuffleOn.value = newVal
    }

    fun cycleRepeat() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _repeatMode.value = next
    }

    // ── 睡眠定时功能 ───────────────────────────────────────
    fun startSleepTimerForMinutes(minutes: Int) {
        startSleepTimer(minutes * 60 * 1000L)
    }

    fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining
            }
            // Timer expired
            controller?.pause()
            _sleepTimerRemainingMs.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = null
    }

    /**
     * 从播放队列中移除指定歌曲（正在播放界面右滑删除；移除当前曲时播放器会自动切到下一首）。
     */
    fun removeFromQueue(song: AudioItem) {
        val c = controller ?: return
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == song.id.toString()) {
                c.removeMediaItem(i)
                break
            }
        }
    }

    // ── 收藏功能 ──────────────────────────────────────────
    fun toggleFavorite(songId: Long) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _favoriteIds.value = current
        saveFavoritesToPrefs(current)
    }

    fun isFavorite(songId: Long): Boolean = _favoriteIds.value.contains(songId)

    private fun loadFavoritesFromPrefs(): Set<Long> {
        val str = prefs.getString("favorites", "") ?: ""
        if (str.isEmpty()) return emptySet()
        return str.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    private fun saveFavoritesToPrefs(ids: Set<Long>) {
        prefs.edit().putString("favorites", ids.joinToString(",")).apply()
    }

    // ── 播放列表功能 ───────────────────────────────────────
    private fun loadPlaylistsFromPrefs() {
        val str = prefs.getString("playlists", "") ?: ""
        if (str.isEmpty()) return
        val list = str.split("||").mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 2) {
                val id = parts[0]
                val name = parts[1]
                val songIds = if (parts.size == 3 && parts[2].isNotEmpty()) {
                    parts[2].split(",").mapNotNull { idStr -> idStr.toLongOrNull() }
                } else emptyList()
                Playlist(id, name, songIds)
            } else null
        }
        _playlists.value = list
    }

    private fun savePlaylistsToPrefs(list: List<Playlist>) {
        val str = list.joinToString("||") { pl ->
            "${pl.id}|${pl.name}|${pl.songIds.joinToString(",")}"
        }
        prefs.edit().putString("playlists", str).apply()
    }

    fun createPlaylist(name: String) {
        val current = _playlists.value.toMutableList()
        current.add(Playlist(id = System.currentTimeMillis().toString(), name = name, songIds = emptyList()))
        _playlists.value = current
        savePlaylistsToPrefs(current)
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = current[index]
            if (!pl.songIds.contains(songId)) {
                current[index] = pl.copy(songIds = pl.songIds + songId)
                _playlists.value = current
                savePlaylistsToPrefs(current)
            }
        }
    }

    // ── 最近播放 ──────────────────────────────────────────
    private fun addToRecentlyPlayed(song: AudioItem) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == song.id }
        current.add(0, song)
        _recentlyPlayed.value = current.take(20)
    }

    // ── 搜索 ──────────────────────────────────────────────
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun filteredSongs(): List<AudioItem> {
        val q = _searchQuery.value.trim().lowercase()
        if (q.isEmpty()) return _songs.value
        return _songs.value.filter {
            it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
        }
    }

    // ── 分组辅助 ──────────────────────────────────────────
    fun songsByAlbum(): Map<String, List<AudioItem>> =
        _songs.value.groupBy { it.album }

    fun songsByArtist(): Map<String, List<AudioItem>> =
        _songs.value.groupBy { it.artist }

    fun getFavoriteSongs(): List<AudioItem> =
        _songs.value.filter { _favoriteIds.value.contains(it.id) }

    // ── 格式化时间 ────────────────────────────────────────
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    override fun onCleared() {
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}