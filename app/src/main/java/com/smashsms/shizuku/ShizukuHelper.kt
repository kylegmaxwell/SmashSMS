package com.smashsms.shizuku

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"
    private const val SHELL_PACKAGE = "com.android.shell"

    @Volatile
    private var writeSmsAppOpGranted = false

    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Shizuku.newProcess", e)
            null
        }
    }

    fun isBinderReady(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean {
        if (!isBinderReady()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking Shizuku permission", e)
            false
        }
    }

    internal fun buildDeleteCommand(sender: String): Array<String> {
        val escapedSender = sender.replace("'", "''")
        return arrayOf(
            "content",
            "delete",
            "--uri",
            "content://sms",
            "--where",
            "address='$escapedSender'",
        )
    }

    internal fun buildRestoreCommand(
        sender: String,
        body: String,
        timestamp: Long,
    ): Array<String> {
        return arrayOf(
            "content",
            "insert",
            "--uri",
            "content://sms/inbox",
            "--bind",
            "address:s:$sender",
            "--bind",
            "body:s:$body",
            "--bind",
            "date:l:$timestamp",
            "--bind",
            "read:i:0",
            "--bind",
            "type:i:1",
        )
    }

    private fun ensureWriteSmsAppOp(): Boolean {
        if (writeSmsAppOpGranted) return true
        val ok = runShizukuCommand(
            arrayOf("appops", "set", SHELL_PACKAGE, "WRITE_SMS", "allow"),
            actionLabel = "appops WRITE_SMS allow",
        )
        if (ok) {
            writeSmsAppOpGranted = true
        }
        return ok
    }

    /**
     * Executes `content delete --uri content://sms --where "address='<ESCAPED_SENDER>'"`
     * via Shizuku.newProcess().
     */
    fun deleteSmsByAddress(sender: String): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Shizuku unavailable or permission denied; skipping telephony delete")
            return false
        }
        ensureWriteSmsAppOp()
        return runShizukuCommand(
            cmd = buildDeleteCommand(sender),
            actionLabel = "Deleted SMS for $sender",
        )
    }

    /**
     * Restores a previously deleted SMS into `content://sms/inbox` via Shizuku so it reappears
     * in Google Messages (where the user can add the sender to Contacts).
     */
    fun restoreSmsToInbox(
        sender: String,
        body: String,
        timestamp: Long,
    ): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Shizuku unavailable or permission denied; skipping telephony restore")
            return false
        }
        ensureWriteSmsAppOp()
        return runShizukuCommand(
            cmd = buildRestoreCommand(sender = sender, body = body, timestamp = timestamp),
            actionLabel = "Restored SMS from $sender",
        )
    }

    private fun runShizukuCommand(
        cmd: Array<String>,
        actionLabel: String,
    ): Boolean {
        val method = newProcessMethod ?: return false
        var process: ShizukuRemoteProcess? = null
        return try {
            process = method.invoke(null, cmd, null, null) as ShizukuRemoteProcess
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "$actionLabel failed (exit=$exitCode): stdout=$stdout stderr=$stderr")
            } else {
                Log.i(TAG, "$actionLabel: $stdout")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing Shizuku command ($actionLabel)", e)
            false
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
