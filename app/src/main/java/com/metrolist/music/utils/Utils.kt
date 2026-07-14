package com.metrolist.music.utils

import timber.log.Timber

/** Minimal stand-in for Meld's reportException (no crash-reporting backend here). */
fun reportException(throwable: Throwable) {
    Timber.tag("Spotufi").e(throwable)
}
