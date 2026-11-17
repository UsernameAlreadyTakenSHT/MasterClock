package com.masterclock.app.logic

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import com.masterclock.core.R

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var beepId: Int = -1
    private var gongId: Int = -1
    private var finalBeepId: Int = -1
    private var switchId: Int = -1
    
    private var currentMediaStream: Boolean? = null
    private var currentVolume: Float = 1.0f

    // SoundPool.load() decodes asynchronously and returns before the sound is actually playable;
    // play()ing a ready-but-not-loaded id is a silent no-op (previewing a sound right after it's
    // (re)loaded could play nothing). These track real load state so play() can defer instead of drop.
    private val loadedIds = mutableSetOf<Int>()
    private val pendingPlayIds = mutableSetOf<Int>()

    // The bundled sound each custom one stands in for, by sample id, until it has decoded. A file
    // the picker handed over can open and still not be a sound the device decodes; that is only
    // known once the load completes, long after loadSound has returned its id.
    private val defaultFor = mutableMapOf<Int, Int>()

    private fun initSoundPool(useMedia: Boolean) {
        if (currentMediaStream == useMedia && soundPool != null) return

        soundPool?.release()
        loadedIds.clear()
        pendingPlayIds.clear()
        defaultFor.clear()

        val usage = if (useMedia) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
                defaultFor.remove(sampleId)
                if (pendingPlayIds.remove(sampleId)) {
                    pool.play(sampleId, currentVolume, currentVolume, 1, 0, 1f)
                }
            } else {
                // A failed load leaves its id permanently unplayable; without this branch the
                // failure is invisible (play() just defers forever on an id that never loads).
                val wasAwaited = pendingPlayIds.remove(sampleId)
                Log.e("SoundManager", "Sound load failed: sampleId=$sampleId status=$status")
                // A custom sound that would not decode gives its place back to the bundled one. It
                // only logged, and the sound was then silent for good -- the flag's beep, say --
                // under a setting that still read "Custom sound selected".
                defaultFor.remove(sampleId)?.let { resId ->
                    pool.unload(sampleId)
                    val bundled = pool.load(context, resId, 1)
                    when (sampleId) {
                        beepId -> beepId = bundled
                        gongId -> gongId = bundled
                        finalBeepId -> finalBeepId = bundled
                        switchId -> switchId = bundled
                    }
                    // What was asked of it while it was still decoding is played by its stand-in.
                    if (wasAwaited && bundled > 0) pendingPlayIds.add(bundled)
                }
            }
        }

        currentMediaStream = useMedia
    }

    fun setVolume(volume: Float) {
        currentVolume = volume
    }

    fun loadSounds(settings: ChessClockSettings) {
        initSoundPool(settings.audioOutputMedia)
        currentVolume = settings.soundsVolume

        // If the pool survived initSoundPool's early return, the previous samples are still
        // loaded in it; unload them or repeated loadSounds() calls accumulate duplicates until
        // SoundPool.load() starts failing.
        soundPool?.let { pool ->
            for (oldId in intArrayOf(beepId, gongId, finalBeepId, switchId)) {
                if (oldId > 0) {
                    pool.unload(oldId)
                    loadedIds.remove(oldId)
                    pendingPlayIds.remove(oldId)
                    defaultFor.remove(oldId)
                }
            }
        }

        beepId = loadSound(settings.customBeepUri, R.raw.beep)
        gongId = loadSound(settings.customGongUri, R.raw.gong)
        finalBeepId = loadSound(settings.customFinalBeepUri, R.raw.finalbeep)
        switchId = loadSound(settings.customSwitchUri, R.raw.switch_sound)
    }

    private fun loadSound(customUri: String?, defaultResId: Int): Int {
        val pool = soundPool ?: return -1
        if (!customUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(customUri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    val id = pool.load(fd.fileDescriptor, 0, fd.statSize, 1)
                    // Zero is a load refused on the spot, and falls through to the bundled sound
                    // like a file that would not open.
                    if (id > 0) {
                        defaultFor[id] = defaultResId
                        return id
                    }
                    Log.e("SoundManager", "Custom sound refused: $customUri")
                }
            } catch (e: Exception) {
                Log.e("SoundManager", "Failed to load custom sound: $customUri", e)
            }
        }
        return pool.load(context, defaultResId, 1)
    }

    /**
     * The sounds that have loaded and will play, by name. For a test: a custom sound that would not
     * decode left its place silent for good, and nothing but an ear could tell.
     */
    fun readySounds(): Set<String> = buildSet {
        if (beepId in loadedIds) add("beep")
        if (gongId in loadedIds) add("gong")
        if (finalBeepId in loadedIds) add("final")
        if (switchId in loadedIds) add("switch")
    }

    fun playShortBeep() = play(beepId)
    fun playLongBeep() = play(finalBeepId)
    fun playTripleBeep() = play(finalBeepId)
    fun playGong() = play(gongId)
    fun playSwitch() {
        if (switchId <= 0) {
            Log.w("SoundManager", "Switch sound not loaded, attempting reload...")
            switchId = loadSound(null, R.raw.switch_sound)
        }
        play(switchId)
    }

    private fun play(soundId: Int) {
        val pool = soundPool
        // SoundPool.load() returns 0 on failure, not -1, so both are invalid ids here.
        if (pool != null && soundId > 0) {
            if (soundId !in loadedIds) {
                // Still decoding; play it as soon as onLoadComplete fires instead of dropping it.
                pendingPlayIds.add(soundId)
                return
            }
            val result = pool.play(soundId, currentVolume, currentVolume, 1, 0, 1f)
            if (result == 0) {
                Log.e("SoundManager", "Failed to play sound: $soundId (SoundPool.play returned 0)")
            }
        } else {
            Log.e("SoundManager", "Cannot play sound: pool=$pool, soundId=$soundId")
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
