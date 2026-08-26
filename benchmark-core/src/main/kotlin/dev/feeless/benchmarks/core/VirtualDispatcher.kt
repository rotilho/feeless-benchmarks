package dev.feeless.benchmarks.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@Suppress("PropertyName")
val Dispatchers.VIRTUAL: CoroutineDispatcher
    get() = VirtualDispatcherHolder.dispatcher

private object VirtualDispatcherHolder {
    val dispatcher: CoroutineDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
}
