package com.smashsms

import com.smashsms.shizuku.ShizukuHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ShizukuHelperTest {

    @Test
    fun buildDeleteCommandUsesExpectedContentDeleteArguments() {
        val cmd = ShizukuHelper.buildDeleteCommand("+15551234567")
        assertArrayEquals(
            arrayOf(
                "content",
                "delete",
                "--uri",
                "content://sms",
                "--where",
                "address='+15551234567'",
            ),
            cmd,
        )
    }

    @Test
    fun buildDeleteCommandEscapesSingleQuotesInSenderAddress() {
        val cmd = ShizukuHelper.buildDeleteCommand("' OR '1'='1")
        assertArrayEquals(
            arrayOf(
                "content",
                "delete",
                "--uri",
                "content://sms",
                "--where",
                "address=''' OR ''1''=''1'",
            ),
            cmd,
        )
    }

    @Test
    fun buildRestoreCommandUsesExpectedContentInsertArguments() {
        val cmd = ShizukuHelper.buildRestoreCommand(
            sender = "+15551234567",
            body = "Hello there!",
            timestamp = 1_700_000_000_000L,
        )
        assertArrayEquals(
            arrayOf(
                "content",
                "insert",
                "--uri",
                "content://sms/inbox",
                "--bind",
                "address:s:+15551234567",
                "--bind",
                "body:s:Hello there!",
                "--bind",
                "date:l:1700000000000",
                "--bind",
                "read:i:0",
                "--bind",
                "type:i:1",
            ),
            cmd,
        )
    }
}
