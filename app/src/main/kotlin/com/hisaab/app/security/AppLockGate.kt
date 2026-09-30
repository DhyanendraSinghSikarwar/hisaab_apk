package com.hisaab.app.security

/**
 * Leaving the app for the file picker or Google's consent screen stops the activity, which would
 * otherwise lock the app on return. Callers set this just before launching such a screen.
 */
object AppLockGate {
    @Volatile private var skip = false
    fun skipNextLock() { skip = true }
    fun consumeSkip(): Boolean = skip.also { skip = false }
}
