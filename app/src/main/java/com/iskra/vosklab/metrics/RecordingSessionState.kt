package com.iskra.vosklab.metrics

import java.util.concurrent.atomic.AtomicLong

class RecordingSessionState {
    private val _micStartMs = AtomicLong(0L)
    private val _firstPcmMs = AtomicLong(0L)
    private val _speechOnsetMs = AtomicLong(0L)
    private val _firstPartialMs = AtomicLong(0L)
    private val _firstWordMs = AtomicLong(0L)
    private val _stopMs = AtomicLong(0L)
    private val _finalMs = AtomicLong(0L)
    private val _voskProcessingTimeMs = AtomicLong(0L)

    val micStartMs: Long get() = _micStartMs.get()
    val firstPcmMs: Long get() = _firstPcmMs.get()
    val speechOnsetMs: Long get() = _speechOnsetMs.get()
    val firstPartialMs: Long get() = _firstPartialMs.get()
    val firstWordMs: Long get() = _firstWordMs.get()
    val stopMs: Long get() = _stopMs.get()
    val finalMs: Long get() = _finalMs.get()
    val voskProcessingTimeMs: Long get() = _voskProcessingTimeMs.get()

    fun recordMicStart(timeMs: Long = System.currentTimeMillis()) {
        _micStartMs.compareAndSet(0L, timeMs)
    }

    fun recordFirstPcm(timeMs: Long = System.currentTimeMillis()) {
        _firstPcmMs.compareAndSet(0L, timeMs)
    }

    fun recordSpeechOnset(timeMs: Long = System.currentTimeMillis()) {
        _speechOnsetMs.compareAndSet(0L, timeMs)
    }

    fun recordFirstPartial(timeMs: Long = System.currentTimeMillis()) {
        _firstPartialMs.compareAndSet(0L, timeMs)
    }

    fun recordFirstWord(timeMs: Long = System.currentTimeMillis()) {
        _firstWordMs.compareAndSet(0L, timeMs)
    }

    fun recordStop(timeMs: Long = System.currentTimeMillis()) {
        _stopMs.compareAndSet(0L, timeMs)
    }

    fun recordFinal(timeMs: Long = System.currentTimeMillis()) {
        _finalMs.compareAndSet(0L, timeMs)
    }

    fun addVoskProcessingTime(durationMs: Long) {
        if (durationMs > 0) {
            _voskProcessingTimeMs.addAndGet(durationMs)
        }
    }

    fun getDiagnosticTimestamps(): DiagnosticTimestamps {
        return DiagnosticTimestamps(
            micStartMs = _micStartMs.get(),
            firstPcmMs = _firstPcmMs.get(),
            speechOnsetMs = _speechOnsetMs.get(),
            firstPartialMs = _firstPartialMs.get(),
            firstWordMs = _firstWordMs.get(),
            finalMs = _finalMs.get(),
            stopMs = _stopMs.get()
        )
    }
}
