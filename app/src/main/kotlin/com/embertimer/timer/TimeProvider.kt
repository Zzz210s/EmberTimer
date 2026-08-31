package com.embertimer.timer

interface TimeProvider {
    fun now(): Long
    fun elapsedRealtime(): Long
}
