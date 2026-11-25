package com.smashsms.shizuku

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.Method

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"

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

    /**
     * Executes `content delete --uri content://sms --where "address='<ESCAPED_SENDER>'"`
     * via Shizuku.newProcess().
     */
    fun deleteSmsByAddress(sender: String): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Shizuku unavailable or permission denied; skipping telephony delete")
            return false
        }
        val method = newProcessMethod ?: return false
        val cmd = buildDeleteCommand(sender)

        var process: ShizukuRemoteProcess? = null
        return try {
            process = method.invoke(null, cmd, null, null) as ShizukuRemoteProcess
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "content delete failed (exit=$exitCode): stdout=$stdout stderr=$stderr")
            } else {
                Log.i(TAG, "Deleted SMS for $sender: $stdout")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing Shizuku content delete for $sender", e)
            false
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
