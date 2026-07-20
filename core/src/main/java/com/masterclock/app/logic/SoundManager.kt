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

    private fun initSoundPool(useMedia: Boolean) {
        if (currentMediaStream == useMedia && soundPool != null) return

        soundPool?.release()
        loadedIds.clear()
        pendingPlayIds.clear()

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
                if (pendingPlayIds.remove(sampleId)) {
                    pool.play(sampleId, currentVolume, currentVolume, 1, 0, 1f)
                }
            }
        }

        currentMediaStream = useMedia
    }

    fun loadSounds(settings: ChessClockSettings) {
        initSoundPool(settings.audioOutputMedia)
        currentVolume = settings.soundsVolume
        
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
                    return pool.load(fd.fileDescriptor, 0, fd.statSize, 1)
                }
            } catch (e: Exception) {
                Log.e("SoundManager", "Failed to load custom sound: $customUri", e)
            }
        }
        return pool.load(context, defaultResId, 1)
    }

    fun playShortBeep() = play(beepId)
    fun playLongBeep() = play(finalBeepId)
    fun playTripleBeep() = play(finalBeepId)
    fun playGong() = play(gongId)
    fun playSwitch() {
        if (switchId == -1) {
            Log.w("SoundManager", "Switch sound not loaded, attempting reload...")
            switchId = loadSound(null, R.raw.switch_sound)
        }
        play(switchId)
    }

    private fun play(soundId: Int) {
        val pool = soundPool
        if (pool != null && soundId != -1) {
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
