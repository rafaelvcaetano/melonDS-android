package me.magnum.melonds.common

import io.reactivex.Scheduler

class Schedulers(val backgroundThreadScheduler: Scheduler, val uiThreadScheduler: Scheduler)