package com.iskra.vosklab.audio

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    var isPlaying: Boolean = false
        private set

    fun playFile(file: File, onCompletionListener: (() -> Unit)? = null) {
        stop()
        if (!file.exists()) return

        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener {
                this@AudioPlayer.isPlaying = false
                onCompletionListener?.invoke()
            }
            start()
        }
        isPlaying = true
    }

    fun stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        }
        isPlaying = false
    }
}
