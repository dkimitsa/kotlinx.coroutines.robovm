/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused")

package kotlinx.coroutines.robovm

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import org.robovm.apple.dispatch.DispatchBlock
import org.robovm.apple.dispatch.DispatchBlockFlags
import org.robovm.apple.dispatch.DispatchQueue
import org.robovm.apple.foundation.NSObject
import org.robovm.rt.bro.annotation.Callback
import org.robovm.rt.bro.ptr.FunctionPtr
import org.robovm.rt.bro.ptr.VoidPtr
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*

/**
 * Dispatches execution onto iOS [DispatchQueue].
 *
 * This class provides type-safety and a point for future extensions.
 */
@InternalCoroutinesApi
sealed class DispatchQueueDispatcher : MainCoroutineDispatcher(), Delay {
    /**
     * Returns dispatcher that executes coroutines immediately when it is already in the right context
     * (current dispatch queue is the same as one in Dispatcher) without an additional [re-dispatch][CoroutineDispatcher.dispatch].
     * This dispatcher does not use [DispatchQueue.async] when current dispatch queue is the same as one in Dispatcher.
     *
     * Immediate dispatcher is safe from stack overflows and in case of nested invocations forms event-loop similar to [Dispatchers.Unconfined].
     * The event loop is an advanced topic and its implications can be found in [Dispatchers.Unconfined] documentation.
     *
     * Example of usage:
     * ```
     * suspend fun updateUiElement(text: String) {
     *   /*
     *    * If it is known that updateUiElement can be invoked both from the Main thread and from other threads,
     *    * `immediate` dispatcher is used as a performance optimization to avoid unnecessary dispatch.
     *    *
     *    * In that case, when `updateUiElement` is invoked from the Main thread, `uiElement.text` will be
     *    * invoked immediately without any dispatching, otherwise, the `Dispatchers.Main` dispatch cycle via
     *    * `DispatchQueue.async` will be triggered.
     *    */
     *   withContext(Dispatchers.Main.immediate) {
     *     uiElement.text = text
     *   }
     *   // Do context-independent logic such as logging
     * }
     * ```
     */
    abstract override val immediate: DispatchQueueDispatcher
}

@InternalCoroutinesApi
internal class RoboVmDispatcherFactory : MainDispatcherFactory {

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>) =
            DispatchQueueContext(DispatchQueue.getMainQueue())

    override fun hintOnError(): String = "For tests Dispatchers.setMain from kotlinx-coroutines-test module can be used"

    override val loadPriority: Int
        get() = Int.MAX_VALUE / 2
}

/**
 * Represents an arbitrary [DispatchQueue] as a implementation of [CoroutineDispatcher]
 * with an optional [name] for nicer debugging
 */
@InternalCoroutinesApi
@JvmName("from") // this is for a nice Java API, see issue #255
@JvmOverloads
fun DispatchQueue.asCoroutineDispatcher(name: String? = null): DispatchQueueDispatcher =
        DispatchQueueContext(this, name)

@InternalCoroutinesApi
@JvmField
@Deprecated("Use Dispatchers.Main instead", level = DeprecationLevel.HIDDEN)
internal val Main: DispatchQueueDispatcher? = runCatching { DispatchQueueContext(DispatchQueue.getMainQueue()) }.getOrNull()


/**
 * helper object that tracks associated objects at java side and also releases when callback from native part triggered
 */
private object DispatchQueueAssociatedValues {
    /**
     * We need an native object pointer to be used as key to pass as key parameter to dispatchQueue.getSpecific to get
     * dispatchQueue associated object. This associated object to be used to compare dispatchQueue with current active one
     */
    val key: VoidPtr = NSObject().`as`(VoidPtr::class.java)

    /**
     * Set to keep associated key from being de-allocated by garbage collector
     */
    private val dispatchQueueAssociatedKeys = mutableSetOf<VoidPtr>()

    private val releaseFunction: FunctionPtr = run {
        val callback = javaClass.getDeclaredMethod("releaseCallback", VoidPtr::class.java)
        FunctionPtr(callback)
    }

    /**
     * attaches specific value to queue
     */
    fun attachSpecificValue(dispatchQueue: DispatchQueue) {
        kotlin.synchronized(dispatchQueueAssociatedKeys) {
            if (dispatchQueue.getSpecific(key) == null) {
                val specificValue = NSObject().`as`(VoidPtr::class.java)
                dispatchQueue.setSpecific(key, specificValue, releaseFunction)
                // sanity
                if (dispatchQueue.getSpecific(key) != specificValue)
                    throw IllegalStateException("failed to attach queueSpecificKey!")
            }
        }
    }

    @JvmStatic
    @Callback
    fun releaseCallback(key: VoidPtr) {
        kotlin.synchronized(dispatchQueueAssociatedKeys) {
            dispatchQueueAssociatedKeys.remove(key)
        }
    }
}


/**
 * Implements [CoroutineDispatcher] on top of an arbitrary iOS [DispatchQueue].
 */
@InternalCoroutinesApi
internal class DispatchQueueContext private constructor(
        private val dispatchQueue: DispatchQueue,
        private val name: String?,
        private val invokeImmediately: Boolean
) : DispatchQueueDispatcher(), Delay {
    /**
     * Creates [CoroutineDispatcher] for the given iOS [DispatchQueue].
     *
     * @param dispatchQueue a DispatchQueue
     * @param name an optional name for debugging.
     */
    constructor(
            dispatchQueue: DispatchQueue,
            name: String? = null
    ) : this(dispatchQueue, name, false)

    init {
        DispatchQueueAssociatedValues.attachSpecificValue(dispatchQueue)
    }


    @Volatile
    private var _immediate: DispatchQueueContext? = if (invokeImmediately) this else null

    override val immediate: DispatchQueueContext = _immediate
            ?: DispatchQueueContext(dispatchQueue, name, true).also { _immediate = it }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !invokeImmediately || DispatchQueue.getCurrentSpecific(DispatchQueueAssociatedValues.key) != dispatchQueue.getSpecific(DispatchQueueAssociatedValues.key)
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatchQueue.async(block)
    }

    @ExperimentalCoroutinesApi
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val block = Runnable {
            with(continuation) { resumeUndispatched(Unit) }
        }
        val dispatchBlock = DispatchBlock.create(DispatchBlockFlags.None, block)
        dispatchQueue.after(timeMillis, TimeUnit.MILLISECONDS, dispatchBlock)
        continuation.invokeOnCancellation { DispatchBlock.cancel(dispatchBlock) }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val dispatchBlock = DispatchBlock.create(DispatchBlockFlags.None, block)
        dispatchQueue.after(timeMillis, TimeUnit.MILLISECONDS, dispatchBlock)
        return object : DisposableHandle {
            override fun dispose() {
                DispatchBlock.cancel(dispatchBlock)
            }
        }
    }

    override fun toString(): String = toStringInternalImpl() ?: run {
        val str = name ?: dispatchQueue.label
        if (invokeImmediately) "$str.immediate" else str
    }

    override fun equals(other: Any?): Boolean = other is DispatchQueueContext && other.dispatchQueue === dispatchQueue
    override fun hashCode(): Int = System.identityHashCode(dispatchQueue)
}

