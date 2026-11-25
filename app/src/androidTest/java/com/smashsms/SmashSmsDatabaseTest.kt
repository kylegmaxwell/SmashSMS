package com.smashsms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smashsms.data.AllowedSender
import com.smashsms.data.AllowedSenderDao
import com.smashsms.data.CustomKeyword
import com.smashsms.data.CustomKeywordDao
import com.smashsms.data.QuarantinedMessage
import com.smashsms.data.QuarantinedMessageDao
import com.smashsms.data.SmashSmsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmashSmsDatabaseTest {

    private lateinit var db: SmashSmsDatabase
    private lateinit var dao: QuarantinedMessageDao
    private lateinit var keywordDao: CustomKeywordDao
    private lateinit var allowedDao: AllowedSenderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmashSmsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.quarantinedMessageDao()
        keywordDao = db.customKeywordDao()
        allowedDao = db.allowedSenderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertObserveDeduplicateAndClear() = runBlocking {
        val older = QuarantinedMessage(
            sender = "+15550001111",
            body = "Chip in via ActBlue",
            timestamp = 1_000L,
            matchedRule = "ActBlue",
            source = "SMS",
            deletedFromTelephony = true,
        )
        val newer = QuarantinedMessage(
            sender = "+15550002222",
            body = "Reply STOP to end",
            timestamp = 20_000L,
            matchedRule = "Stop to End",
            source = "Notification",
            deletedFromTelephony = false,
        )

        dao.insert(older)
        dao.insert(newer)

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("+15550002222", all[0].sender)
        assertEquals("+15550001111", all[1].sender)

        assertTrue(dao.existsRecent("+15550002222", "Reply STOP to end", sinceTimestamp = 10_000L))
        assertFalse(dao.existsRecent("+15550001111", "Chip in via ActBlue", sinceTimestamp = 10_000L))

        dao.deleteById(all[0].id)
        assertEquals(1, dao.observeAll().first().size)

        dao.clearAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun customKeywordsAndAllowedSendersCrud() = runBlocking {
        val kwId = keywordDao.insert(CustomKeyword(keyword = "crypto giveaway"))
        assertEquals(1, keywordDao.getEnabled().size)

        keywordDao.setEnabled(kwId, false)
        assertTrue(keywordDao.getEnabled().isEmpty())

        keywordDao.deleteById(kwId)
        assertTrue(keywordDao.observeAll().first().isEmpty())

        val allowId = allowedDao.insert(AllowedSender(sender = "+15550192834"))
        val saved = allowedDao.getAll()
        assertEquals(1, saved.size)
        assertTrue(saved[0].matches("(555) 019-2834"))

        allowedDao.deleteById(allowId)
        assertTrue(allowedDao.getAll().isEmpty())
    }
}
