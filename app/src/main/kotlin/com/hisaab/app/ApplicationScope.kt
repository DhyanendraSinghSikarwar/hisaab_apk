package com.hisaab.app

import javax.inject.Qualifier

/** A scope that lives as long as the process, for work that must outlive a screen or a receiver. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
