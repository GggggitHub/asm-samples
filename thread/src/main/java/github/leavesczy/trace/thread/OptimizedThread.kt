package github.leavesczy.trace.thread

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * @Author: leavesCZY
 * @Github: https://github.com/leavesCZY
 * @Desc:
 */
class OptimizedThread(runnable: Runnable?, name: String?, className: String) :
    Thread(runnable, generateThreadName(name, className)) {
    companion object {
        const val TAG = "OptimizedThread"

        private val threadId = AtomicInteger(0)

        private fun generateThreadName(name: String?, className: String): String {
            var threadName = name+"-"+className + "-" + threadId.getAndIncrement() + if (name.isNullOrBlank()) {
                ""
            } else {
                "-$name"
            }
            Log.e(TAG, "generateThreadName: $threadName")
            return threadName
        }

    }

    constructor(runnable: Runnable, className: String) : this(runnable, null, className)

    constructor(name: String, className: String) : this(null, name, className)

}