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
import com.applemusic.clone.model.ListeningRecord
import com.applemusic.clone.model.LrcLine
import com.applemusic.clone.model.LyricsCacheEntry
import com.applemusic.clone.model.Playlist
import com.applemusic.clone.settings.AppSettingsKeys
import com.applemusic.clone.service.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val KEY_LISTENING_RECORDS = "listening_records_v1"
private const val KEY_RECENTLY_PLAYED_IDS = "recently_played_ids_v1"
private const val MAX_LISTENING_RECORDS = 2000
private const val MIN_LISTENING_RECORD_MS = 1000L

data class PlaylistImportResult(
    val importedPlaylists: Int,
    val importedSongs: Int,
    val missingSongs: Int,
    val importedListeningRecords: Int = 0,
    val importedRecentlyPlayed: Int = 0
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    private val prefs = application.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    private val appSettingsPrefs = application.getSharedPreferences(AppSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private var lyricsSearchIndexJob: Job? = null

    // ── 歌曲列表 ──────────────────────────────────────────
    private val _songs = MutableStateFlow<List<AudioItem>>(emptyList())
    val songs: StateFlow<List<AudioItem>> = _songs.asStateFlow()

    private val _lyricsSearchIndex = MutableStateFlow<Map<Long, String>>(emptyMap())
    val lyricsSearchIndex: StateFlow<Map<Long, String>> = _lyricsSearchIndex.asStateFlow()

    private val _lyricsCacheEntries = MutableStateFlow<List<LyricsCacheEntry>>(emptyList())
    val lyricsCacheEntries: StateFlow<List<LyricsCacheEntry>> = _lyricsCacheEntries.asStateFlow()

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

    // ── 播放完当前歌曲后暂停 ──────────────────────────────
    private var pauseAfterCurrentSong = false


    // ── 搜索 ──────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── 收藏 ──────────────────────────────────────────────
    private val _favoriteIds = MutableStateFlow<Set<Long>>(loadFavoritesFromPrefs())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // ── 已隐藏的专辑（长按删除用，持久化到 prefs） ────────
    private val _hiddenAlbums = MutableStateFlow<Set<String>>(loadHiddenAlbums())
    val hiddenAlbums: StateFlow<Set<String>> = _hiddenAlbums.asStateFlow()
    private fun loadHiddenAlbums(): Set<String> = prefs.getStringSet("hidden_albums", emptySet()) ?: emptySet()
    fun hideAlbum(album: String) {
        val newSet = _hiddenAlbums.value + album
        _hiddenAlbums.value = newSet
        prefs.edit().putStringSet("hidden_albums", newSet).apply()
    }
    fun unhideAlbum(album: String) {
        val newSet = _hiddenAlbums.value - album
        _hiddenAlbums.value = newSet
        prefs.edit().putStringSet("hidden_albums", newSet).apply()
    }

    // ── 最近播放 ──────────────────────────────────────────
    private val _recentlyPlayed = MutableStateFlow<List<AudioItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<AudioItem>> = _recentlyPlayed.asStateFlow()

    private val _listeningRecords = MutableStateFlow<List<ListeningRecord>>(loadListeningRecords())
    val listeningRecords: StateFlow<List<ListeningRecord>> = _listeningRecords.asStateFlow()

    // ── AI 播放列表 ───────────────────────────────────────
    private val _aiPrompt = MutableStateFlow("")
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    private val _aiIsLoading = MutableStateFlow(false)
    val aiIsLoading: StateFlow<Boolean> = _aiIsLoading.asStateFlow()

    private val _aiGeneratedSongs = MutableStateFlow<List<AudioItem>>(emptyList())
    val aiGeneratedSongs: StateFlow<List<AudioItem>> = _aiGeneratedSongs.asStateFlow()

    private val _aiTags = MutableStateFlow<List<String>>(emptyList())
    val aiTags: StateFlow<List<String>> = _aiTags.asStateFlow()

    private val _aiEmotions = MutableStateFlow<List<String>>(emptyList())
    val aiEmotions: StateFlow<List<String>> = _aiEmotions.asStateFlow()

    private val _aiResponseText = MutableStateFlow("")
    val aiResponseText: StateFlow<String> = _aiResponseText.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    fun setAiPrompt(prompt: String) { _aiPrompt.value = prompt }

    fun generateAiPlaylist(prompt: String) {
        _aiPrompt.value = prompt
        _aiIsLoading.value = true
        _aiError.value = null
        _aiGeneratedSongs.value = emptyList()
        _aiTags.value = emptyList()
        _aiEmotions.value = emptyList()
        _aiResponseText.value = ""
        viewModelScope.launch {
            val result = com.applemusic.clone.data.AiPlaylistGenerator.generate(prompt, _songs.value)
            result.onSuccess { gen ->
                _aiGeneratedSongs.value = gen.matchedSongs
                _aiTags.value = gen.tags
                _aiEmotions.value = gen.emotions
                _aiResponseText.value =
                    if (gen.matchedSongs.isEmpty()) "本地没找到匹配的歌曲"
                    else gen.matchedSongs.take(12).joinToString("\n") { "${it.title} - ${it.artist}" }
            }.onFailure { e ->
                _aiError.value = e.message ?: "AI 请求失败"
            }
            _aiIsLoading.value = false
        }
    }

    fun playAiPlaylist() {
        val songs = _aiGeneratedSongs.value
        if (songs.isNotEmpty()) playList(songs, 0)
    }

    fun saveAiPlaylist() {
        val songs = _aiGeneratedSongs.value
        if (songs.isEmpty()) return
        val name = "AI: ${_aiPrompt.value.take(20)}"
        val songIds = songs.map { it.id }
        val pl = com.applemusic.clone.model.Playlist(
            id = System.currentTimeMillis().toString(),
            name = name,
            songIds = songIds
        )
        val current = _playlists.value.toMutableList()
        current.add(0, pl)
        _playlists.value = current
        savePlaylistsToPrefs(current)
    }

    // ── MediaController ───────────────────────────────────
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var listeningSessionSong: AudioItem? = null
    private var listeningSessionStartedAt: Long = 0L
    private var listeningSessionLastTickAt: Long = 0L
    private var listeningSessionPlayedMs: Long = 0L

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
            val loadedSongs = repository.getLocalAudioFiles()
            _songs.value = loadedSongs
            restoreRecentlyPlayed(loadedSongs)
            rebuildLyricsSearchIndex(loadedSongs)
            _isLoading.value = false
            // 后台元数据获取完成后刷新列表（封面、歌词等）
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                val refreshedSongs = repository.getLocalAudioFiles()
                _songs.value = refreshedSongs
                restoreRecentlyPlayed(refreshedSongs)
                rebuildLyricsSearchIndex(refreshedSongs)
            }
        }
    }

    private fun rebuildLyricsSearchIndex(songs: List<AudioItem>) {
        lyricsSearchIndexJob?.cancel()
        lyricsSearchIndexJob = viewModelScope.launch(Dispatchers.IO) {
            val index = songs
                .asSequence()
                .mapNotNull { song ->
                    val text = searchableLyricsText(song)
                    if (text.isBlank()) null else song.id to text
                }
                .toMap()
            _lyricsSearchIndex.value = index
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
            if (playing) {
                beginListeningSession(_currentSong.value)
                startProgressTracking()
            } else {
                commitListeningSession(clearSession = true)
                progressJob?.cancel()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            commitListeningSession(clearSession = true)
            val id = mediaItem?.mediaId?.toLongOrNull()
            val song = _songs.value.find { it.id == id }
            _currentSong.value = song
            song?.let { addToRecentlyPlayed(it) }
            if (controller?.isPlaying == true) {
                beginListeningSession(song)
            }
            loadLyricsForCurrent()
            if (pauseAfterCurrentSong) {
                pauseAfterCurrentSong = false
                _sleepTimerRemainingMs.value = null
                controller?.pause()
            }
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
                delay(100) // 100ms 轮询让歌词同步更准确（原 500ms 太慢）
            }
        }
    }

    // ── 歌词同步 ──────────────────────────────────────────
    private fun loadLyricsForCurrent() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _currentSong.value ?: return@launch
            var lyrics = emptyList<LrcLine>()
            var source = "none"
            var fallbackLyrics = emptyList<LrcLine>()
            var fallbackSource = "none"

            // 1. 外部 .lrc 文件（用户手工放置，时间轴最准，优先使用）
            if (current.lyricsPath != null) {
                val fromFile = LyricsParser.parse(current.lyricsPath, current.duration)
                if (fromFile.isNotEmpty()) {
                    if (fromFile.any { it.isSynced }) {
                        lyrics = fromFile
                        source = "external_file(${current.lyricsPath})"
                    } else {
                        fallbackLyrics = fromFile
                        fallbackSource = "plain_file(${current.lyricsPath})"
                    }
                }
            }

            // 2. 内嵌歌词（ID3 USLT / M4A ©lyr）—— 仅在外部文件不可用时回退
            if (lyrics.isEmpty()) {
                try {
                    val embedded = com.applemusic.clone.data.EmbeddedLyricsExtractor.extract(current.data)
                    if (!embedded.isNullOrBlank()) {
                        val parsed = LyricsParser.parseFromString(embedded)
                        if (parsed.isNotEmpty()) {
                            lyrics = parsed
                            source = "embedded"
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. 之前缓存到 internal 存储的歌词（来自网络抓取的回退）
            if (lyrics.isEmpty() && current.lyricsPath != null) {
                // 已经在步骤 1 尝试过；如仍未拿到，尝试其他路径
                android.util.Log.d("Lyrics", "External .lrc at ${current.lyricsPath} produced empty list, fallback to online")
            }

            // 4. 在线获取（最后手段）
            val onlineLyricsEnabled = appSettingsPrefs.getBoolean(AppSettingsKeys.ONLINE_LYRICS_ENABLED, true)
            val preferSyncedLyrics = appSettingsPrefs.getBoolean(AppSettingsKeys.PREFER_SYNCED_LYRICS, true)
            if (lyrics.isEmpty() && onlineLyricsEnabled) {
                val online = com.applemusic.clone.data.OnlineMetadataManager.fetchLyrics(
                    current.title,
                    current.artist,
                    current.album,
                    current.duration,
                    preferSynced = preferSyncedLyrics
                )
                if (online != null) {
                    val parsed = LyricsParser.parseFromString(online)
                        .ifEmpty { LyricsParser.parsePlainText(online, current.duration) }
                    if (parsed.isNotEmpty()) {
                        if (parsed.any { it.isSynced }) {
                            lyrics = parsed
                            source = "online"
                        } else if (fallbackLyrics.isEmpty()) {
                            fallbackLyrics = parsed
                            fallbackSource = "online_plain"
                        }
                        repository.cacheLyricsForAudio(current.id, online)?.let { path ->
                            val updatedSong = current.copy(lyricsPath = path)
                            _currentSong.value = updatedSong
                            val updatedSongs = _songs.value.map { song ->
                                if (song.id == current.id) song.copy(lyricsPath = path) else song
                            }
                            _songs.value = updatedSongs
                            rebuildLyricsSearchIndex(updatedSongs)
                        }
                    }
                }
            }

            if (lyrics.isEmpty() && fallbackLyrics.isNotEmpty()) {
                lyrics = fallbackLyrics
                source = fallbackSource
            }

            android.util.Log.d("Lyrics", "Loaded ${lyrics.size} lines for '${current.title}' from $source")
            _lyrics.value = lyrics
            _currentLyricIndex.value = -1
            updateCurrentLyricIndex()
        }
    }

    private fun updateCurrentLyricIndex() {
        val lrcList = _lyrics.value
        if (lrcList.isEmpty()) return
        if (lrcList.none { it.isSynced }) {
            if (_currentLyricIndex.value != -1) _currentLyricIndex.value = -1
            return
        }
        val pos = _currentPositionMs.value
        var idx = lrcList.indexOfLast { it.isSynced && it.timeMs <= pos }
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
        commitListeningSession(clearSession = true)
        c.stop()
        c.setMediaItems(mediaItems, clampedIdx, 0L)
        c.prepare()
        c.play()
        syncQueueFromController()
        val startSong = songs[clampedIdx]
        _currentSong.value = startSong
        addToRecentlyPlayed(startSong)
        beginListeningSession(startSong)
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
        commitListeningSession(clearSession = true)
        c.stop()
        c.setMediaItems(mediaItems, clampedIdx, 0L)
        c.prepare()
        c.play()
        syncQueueFromController()
        val startSong = songs[clampedIdx]
        _currentSong.value = startSong
        addToRecentlyPlayed(startSong)
        beginListeningSession(startSong)
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
        if (c.isPlaying) {
            commitListeningSession(clearSession = true)
            c.pause()
        } else {
            beginListeningSession(_currentSong.value)
            c.play()
        }
    }

    fun skipNext() {
        commitListeningSession(clearSession = true)
        controller?.seekToNextMediaItem()
    }

    /**
     * 跳到播放队列中指定位置的歌曲（不清空队列）
     */
    fun skipToQueueIndex(index: Int) {
        val c = controller ?: return
        commitListeningSession(clearSession = true)
        c.seekTo(index, 0L)
        c.play()
    }

    fun skipPrev() {
        val c = controller ?: return
        if ((c.currentPosition) > 3000) {
            c.seekTo(0)
        } else {
            commitListeningSession(clearSession = true)
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
        pauseAfterCurrentSong = false
        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining
            }
            // 倒计时结束
            if (controller?.isPlaying == true) {
                // 正在播放：等当前歌曲播完再暂停
                pauseAfterCurrentSong = true
                _sleepTimerRemainingMs.value = -1L
            } else {
                controller?.pause()
                _sleepTimerRemainingMs.value = null
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = null
        pauseAfterCurrentSong = false
    }

    fun enablePauseAfterSong() {
        cancelSleepTimer()
        pauseAfterCurrentSong = true
        _sleepTimerRemainingMs.value = -1L // -1 表示「播放完当前歌曲后暂停」模式
    }

    val isPauseAfterSongMode: Boolean get() = _sleepTimerRemainingMs.value == -1L

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

    /**
     * 调整队列中歌曲的顺序（拖拽排序）。
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val c = controller ?: return
        if (fromIndex < 0 || fromIndex >= c.mediaItemCount ||
            toIndex < 0 || toIndex >= c.mediaItemCount) return
        if (fromIndex == toIndex) return
        c.moveMediaItem(fromIndex, toIndex)
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
                val songIds = if (parts.size >= 3 && parts[2].isNotEmpty()) {
                    parts[2].split(",").mapNotNull { idStr -> idStr.toLongOrNull() }
                } else emptyList()
                val cover = if (parts.size >= 4 && parts[3].isNotEmpty()) parts[3] else null
                Playlist(id, name, songIds, cover)
            } else null
        }
        _playlists.value = list
    }

    private fun savePlaylistsToPrefs(list: List<Playlist>) {
        val str = list.joinToString("||") { pl ->
            "${pl.id}|${pl.name}|${pl.songIds.joinToString(",")}|${pl.coverUri ?: ""}"
        }
        prefs.edit().putString("playlists", str).apply()
    }

    fun createPlaylist(name: String): String {
        val id = System.currentTimeMillis().toString()
        val current = _playlists.value.toMutableList()
        current.add(0, Playlist(id = id, name = name, songIds = emptyList()))
        _playlists.value = current
        savePlaylistsToPrefs(current)
        return id
    }

    fun updatePlaylistCover(playlistId: String, coverUri: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(coverUri = coverUri)
            _playlists.value = current
            savePlaylistsToPrefs(current)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            current[index] = current[index].copy(name = newName)
            _playlists.value = current
            savePlaylistsToPrefs(current)
        }
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

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = current[index]
            current[index] = pl.copy(songIds = pl.songIds.filter { it != songId })
            _playlists.value = current
            savePlaylistsToPrefs(current)
        }
    }

    /**
     * 调整播放列表内歌曲的顺序（上下移动按钮）。
     */
    fun movePlaylistSong(playlistId: String, fromIndex: Int, toIndex: Int) {
        val current = _playlists.value.toMutableList()
        val plIndex = current.indexOfFirst { it.id == playlistId }
        if (plIndex < 0) return
        val pl = current[plIndex]
        val ids = pl.songIds.toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return
        val item = ids.removeAt(fromIndex)
        ids.add(toIndex, item)
        current[plIndex] = pl.copy(songIds = ids)
        _playlists.value = current
        savePlaylistsToPrefs(current)
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylistsToPrefs(_playlists.value)
    }

    fun buildPlaylistBackup(
        selectedPlaylistIds: Set<String>,
        includePlaylists: Boolean = true,
        includeListeningHistory: Boolean = false,
        includeRecentlyPlayed: Boolean = false
    ): String {
        val selected = _playlists.value.filter { playlist ->
            includePlaylists && playlist.id in selectedPlaylistIds
        }
        val songsById = _songs.value.associateBy { it.id }
        val root = JSONObject()
            .put("type", "inspire_music_playlist_backup")
            .put("version", 2)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "included",
                JSONObject()
                    .put("playlists", includePlaylists)
                    .put("listeningHistory", includeListeningHistory)
                    .put("recentlyPlayed", includeRecentlyPlayed)
            )
        val playlistsJson = JSONArray()
        selected.forEach { playlist ->
            val songsJson = JSONArray()
            playlist.songIds.forEach { songId ->
                val song = songsById[songId]
                songsJson.put(
                    JSONObject()
                        .put("id", songId)
                        .put("title", song?.title.orEmpty())
                        .put("artist", song?.artist.orEmpty())
                        .put("album", song?.album.orEmpty())
                        .put("duration", song?.duration ?: 0L)
                        .put("path", song?.data.orEmpty())
                        .put("uri", song?.uri?.toString().orEmpty())
                )
            }
            playlistsJson.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("coverUri", playlist.coverUri.orEmpty())
                    .put("songs", songsJson)
            )
        }
        root.put("playlists", playlistsJson)
        if (includeListeningHistory) {
            val recordsJson = JSONArray()
            _listeningRecords.value.forEach { record ->
                recordsJson.put(
                    JSONObject()
                        .put("songId", record.songId)
                        .put("title", record.title)
                        .put("artist", record.artist)
                        .put("album", record.album)
                        .put("genre", record.genre)
                        .put("playedAt", record.playedAt)
                        .put("duration", record.duration)
                )
            }
            root.put("listeningRecords", recordsJson)
        }
        if (includeRecentlyPlayed) {
            val recentJson = JSONArray()
            _recentlyPlayed.value.forEach { song ->
                recentJson.put(
                    JSONObject()
                        .put("id", song.id)
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("album", song.album)
                        .put("duration", song.duration)
                        .put("path", song.data)
                        .put("uri", song.uri.toString())
                )
            }
            root.put("recentlyPlayed", recentJson)
        }
        return root.toString(2)
    }

    fun importPlaylistBackup(json: String): PlaylistImportResult {
        val root = JSONObject(json)
        val playlistsJson = root.optJSONArray("playlists") ?: JSONArray()
        val localSongs = _songs.value
        val imported = mutableListOf<Playlist>()
        var importedSongs = 0
        var missingSongs = 0
        var importedListeningRecords = 0
        var importedRecentlyPlayed = 0

        for (i in 0 until playlistsJson.length()) {
            val playlistJson = playlistsJson.getJSONObject(i)
            val songsJson = playlistJson.optJSONArray("songs") ?: JSONArray()
            val matchedIds = mutableListOf<Long>()
            for (j in 0 until songsJson.length()) {
                val matched = findSongForBackupEntry(songsJson.getJSONObject(j), localSongs)
                if (matched != null) {
                    matchedIds.add(matched.id)
                    importedSongs += 1
                } else {
                    missingSongs += 1
                }
            }
            imported.add(
                Playlist(
                    id = "${System.currentTimeMillis()}-${UUID.randomUUID()}",
                    name = playlistJson.optString("name", "Imported Playlist"),
                    songIds = matchedIds.distinct(),
                    coverUri = playlistJson.optString("coverUri").takeIf { it.isNotBlank() }
                )
            )
        }

        if (imported.isNotEmpty()) {
            val merged = imported + _playlists.value
            _playlists.value = merged
            savePlaylistsToPrefs(merged)
        }

        val recordsJson = root.optJSONArray("listeningRecords")
        if (recordsJson != null) {
            val current = _listeningRecords.value
            val existingKeys = current.map { "${it.songId}:${it.playedAt}" }.toMutableSet()
            val restored = mutableListOf<ListeningRecord>()
            for (i in 0 until recordsJson.length()) {
                val item = recordsJson.optJSONObject(i) ?: continue
                val record = ListeningRecord(
                    songId = item.optLong("songId", -1L),
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    album = item.optString("album"),
                    genre = item.optString("genre"),
                    playedAt = item.optLong("playedAt", 0L),
                    duration = item.optLong("duration", 0L)
                )
                if (record.songId <= 0L || record.playedAt <= 0L) continue
                val key = "${record.songId}:${record.playedAt}"
                if (key !in existingKeys) {
                    existingKeys.add(key)
                    restored.add(record)
                }
            }
            if (restored.isNotEmpty()) {
                val mergedRecords = (restored + current)
                    .sortedByDescending { it.playedAt }
                    .take(MAX_LISTENING_RECORDS)
                _listeningRecords.value = mergedRecords
                saveListeningRecords(mergedRecords)
                importedListeningRecords = restored.size
            }
        }
        val recentJson = root.optJSONArray("recentlyPlayed")
        if (recentJson != null) {
            val restored = mutableListOf<AudioItem>()
            for (i in 0 until recentJson.length()) {
                val item = recentJson.optJSONObject(i) ?: continue
                findSongForBackupEntry(item, localSongs)?.let { song ->
                    if (restored.none { it.id == song.id }) {
                        restored.add(song)
                    }
                }
            }
            if (restored.isNotEmpty()) {
                val merged = (restored + _recentlyPlayed.value)
                    .distinctBy { it.id }
                    .take(20)
                _recentlyPlayed.value = merged
                saveRecentlyPlayed(merged)
                importedRecentlyPlayed = restored.size
            }
        }
        return PlaylistImportResult(imported.size, importedSongs, missingSongs, importedListeningRecords, importedRecentlyPlayed)
    }

    private fun findSongForBackupEntry(entry: JSONObject, songs: List<AudioItem>): AudioItem? {
        val path = entry.optString("path")
        val uri = entry.optString("uri")
        val title = entry.optString("title")
        val artist = entry.optString("artist")
        val album = entry.optString("album")
        val duration = entry.optLong("duration", 0L)
        return songs.firstOrNull { path.isNotBlank() && it.data == path }
            ?: songs.firstOrNull { uri.isNotBlank() && it.uri.toString() == uri }
            ?: songs.firstOrNull {
                it.title.equals(title, ignoreCase = true) &&
                    it.artist.equals(artist, ignoreCase = true) &&
                    it.album.equals(album, ignoreCase = true) &&
                    kotlin.math.abs(it.duration - duration) <= 2500L
            }
            ?: songs.firstOrNull {
                it.title.equals(title, ignoreCase = true) &&
                    it.artist.equals(artist, ignoreCase = true)
            }
    }

    // ── 最近播放 ──────────────────────────────────────────
    private fun addToRecentlyPlayed(song: AudioItem) {
        val current = _recentlyPlayed.value.toMutableList()
        current.removeAll { it.id == song.id }
        current.add(0, song)
        val updated = current.take(20)
        _recentlyPlayed.value = updated
        saveRecentlyPlayed(updated)
    }

    private fun restoreRecentlyPlayed(songs: List<AudioItem>) {
        val ids = loadRecentlyPlayedIds()
        if (ids.isEmpty()) return
        val byId = songs.associateBy { it.id }
        _recentlyPlayed.value = ids.mapNotNull { byId[it] }.take(20)
    }

    private fun loadRecentlyPlayedIds(): List<Long> {
        val raw = prefs.getString(KEY_RECENTLY_PLAYED_IDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val id = array.optLong(i, -1L)
                    if (id > 0L) add(id)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentlyPlayed(songs: List<AudioItem>) {
        val array = JSONArray()
        songs.take(20).forEach { array.put(it.id) }
        prefs.edit().putString(KEY_RECENTLY_PLAYED_IDS, array.toString()).apply()
    }

    // ── 搜索 ──────────────────────────────────────────────
    private fun beginListeningSession(song: AudioItem?) {
        val current = song ?: return
        val now = System.currentTimeMillis()
        if (listeningSessionSong?.id != current.id) {
            commitListeningSession(clearSession = true, now = now)
            listeningSessionSong = current
            listeningSessionStartedAt = now
            listeningSessionPlayedMs = 0L
        }
        if (listeningSessionStartedAt <= 0L) listeningSessionStartedAt = now
        listeningSessionLastTickAt = now
    }

    private fun updateListeningSession(now: Long = System.currentTimeMillis()) {
        val lastTick = listeningSessionLastTickAt
        if (lastTick > 0L && now > lastTick) {
            listeningSessionPlayedMs += now - lastTick
            listeningSessionLastTickAt = now
        }
    }

    private fun commitListeningSession(clearSession: Boolean, now: Long = System.currentTimeMillis()) {
        updateListeningSession(now)
        val song = listeningSessionSong
        val playedMs = listeningSessionPlayedMs.coerceAtLeast(0L)
        if (song != null && playedMs >= MIN_LISTENING_RECORD_MS) {
            addListeningRecord(song, playedMs, listeningSessionStartedAt.takeIf { it > 0L } ?: now)
        }
        listeningSessionLastTickAt = 0L
        listeningSessionPlayedMs = 0L
        if (clearSession) {
            listeningSessionSong = null
            listeningSessionStartedAt = 0L
        }
    }

    private fun addListeningRecord(song: AudioItem, playedDurationMs: Long, playedAt: Long) {
        val previous = _listeningRecords.value.firstOrNull()
        if (previous?.songId == song.id && playedAt - previous.playedAt < 1_000L) return
        val record = ListeningRecord(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre,
            playedAt = playedAt,
            duration = playedDurationMs.coerceIn(0L, song.duration.coerceAtLeast(playedDurationMs))
        )
        val updated = (listOf(record) + _listeningRecords.value).take(MAX_LISTENING_RECORDS)
        _listeningRecords.value = updated
        saveListeningRecords(updated)
    }

    private fun loadListeningRecords(): List<ListeningRecord> {
        val raw = prefs.getString(KEY_LISTENING_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val record = ListeningRecord(
                        songId = item.optLong("songId", -1L),
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        album = item.optString("album"),
                        genre = item.optString("genre"),
                        playedAt = item.optLong("playedAt", 0L),
                        duration = item.optLong("duration", 0L)
                    )
                    if (record.songId > 0L && record.playedAt > 0L) add(record)
                }
            }.sortedByDescending { it.playedAt }.take(MAX_LISTENING_RECORDS)
        }.getOrDefault(emptyList())
    }

    private fun saveListeningRecords(records: List<ListeningRecord>) {
        val array = JSONArray()
        records.take(MAX_LISTENING_RECORDS).forEach { record ->
            array.put(
                JSONObject()
                    .put("songId", record.songId)
                    .put("title", record.title)
                    .put("artist", record.artist)
                    .put("album", record.album)
                    .put("genre", record.genre)
                    .put("playedAt", record.playedAt)
                    .put("duration", record.duration)
            )
        }
        prefs.edit().putString(KEY_LISTENING_RECORDS, array.toString()).apply()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun filteredSongs(): List<AudioItem> {
        val q = _searchQuery.value.trim().lowercase()
        if (q.isEmpty()) return _songs.value
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lyricsIndex = _lyricsSearchIndex.value
        return _songs.value.filter {
            val haystack = buildString {
                append(it.title.lowercase())
                append(' ')
                append(it.artist.lowercase())
                append(' ')
                append(it.album.lowercase())
                append(' ')
                append(lyricsIndex[it.id].orEmpty())
            }
            tokens.all { token -> haystack.contains(token) }
        }
    }

    private fun searchableLyricsText(song: AudioItem): String {
        val path = song.lyricsPath ?: return ""
        return runCatching {
            val parsedText = LyricsParser.parse(path).joinToString(" ") { it.text }
            val rawText = if (parsedText.isBlank()) {
                File(path)
                    .readText(Charsets.UTF_8)
                    .replace(Regex("""\[[^\]]+]"""), " ")
            } else {
                parsedText
            }
            rawText.lowercase()
        }.getOrDefault("")
    }

    // ── 分组辅助 ──────────────────────────────────────────
    fun refreshLyricsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(getApplication<Application>().filesDir, "lyrics")
            if (!dir.exists()) {
                _lyricsCacheEntries.value = emptyList()
                return@launch
            }
            val songsSnapshot = _songs.value
            val entries = dir.listFiles { file ->
                file.isFile && file.extension.equals("lrc", ignoreCase = true)
            }?.map { file ->
                val audioId = file.nameWithoutExtension.toLongOrNull() ?: -1L
                val song = songsSnapshot.firstOrNull { it.id == audioId }
                val parsed = LyricsParser.parse(file.absolutePath, song?.duration ?: 0L)
                val preview = parsed
                    .map { it.text }
                    .firstOrNull { it.isNotBlank() }
                    ?: runCatching {
                        file.readLines(Charsets.UTF_8).firstOrNull { it.isNotBlank() }.orEmpty()
                    }.getOrDefault("")
                LyricsCacheEntry(
                    audioId = audioId,
                    title = song?.title ?: file.nameWithoutExtension,
                    artist = song?.artist ?: "Unknown Artist",
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    updatedAt = file.lastModified(),
                    lineCount = parsed.size,
                    isSynced = parsed.any { it.isSynced },
                    preview = preview
                )
            }?.sortedByDescending { it.updatedAt }.orEmpty()
            _lyricsCacheEntries.value = entries
        }
    }

    fun deleteLyricsCache(entry: LyricsCacheEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            if (entry.audioId > 0L) {
                repository.clearLyricsCacheForAudio(entry.audioId)
                clearLoadedLyricsPaths(setOf(entry.audioId))
            } else {
                runCatching { File(entry.path).delete() }
            }
            refreshLyricsCache()
        }
    }

    fun clearAllLyricsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = _lyricsCacheEntries.value.mapNotNull { it.audioId.takeIf { id -> id > 0L } }.toSet()
            repository.clearAllLyricsCache()
            clearLoadedLyricsPaths(ids)
            _lyricsCacheEntries.value = emptyList()
        }
    }

    private fun clearLoadedLyricsPaths(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val updatedSongs = _songs.value.map { song ->
            if (song.id in ids && isInternalLyricsPath(song.lyricsPath)) {
                song.copy(lyricsPath = null)
            } else {
                song
            }
        }
        _songs.value = updatedSongs
        _currentSong.value?.let { current ->
            if (current.id in ids && isInternalLyricsPath(current.lyricsPath)) {
                _currentSong.value = current.copy(lyricsPath = null)
                _lyrics.value = emptyList()
                _currentLyricIndex.value = -1
            }
        }
        rebuildLyricsSearchIndex(updatedSongs)
    }

    private fun isInternalLyricsPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val lyricsDir = File(getApplication<Application>().filesDir, "lyrics")
        return runCatching {
            File(path).canonicalFile.parentFile == lyricsDir.canonicalFile
        }.getOrDefault(false)
    }

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
        commitListeningSession(clearSession = true)
        progressJob?.cancel()
        lyricsSearchIndexJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
