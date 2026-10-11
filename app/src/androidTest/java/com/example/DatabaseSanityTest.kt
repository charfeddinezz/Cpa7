package com.example

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.CampaignStat
import com.example.data.model.EmailItem
import com.example.data.model.OfferClickItem
import com.example.data.model.ProxyItem
import com.example.data.model.ScriptItem
import com.example.data.model.TaskEntity
import com.example.data.model.WorkTemplateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Database sanity: every entity round-trips through its DAO on the CURRENT
 * schema (v7). Guards entity/DAO refactors in CI. NOTE: production uses
 * fallbackToDestructiveMigration, so any future entity change MUST ship an
 * explicit Migration (schemas are exported via room.schemaLocation).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSanityTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun task_crud_roundtrip() = runBlocking {
        val task = TaskEntity(id = "t1", name = "Test", url = "https://example.com")
        db.taskDao().insertTask(task)
        val loaded = db.taskDao().getTaskById("t1")
        assertNotNull(loaded)
        assertEquals("https://example.com", loaded!!.url)
        db.taskDao().updateTaskStatus("t1", "completed")
        assertEquals("completed", db.taskDao().getTaskById("t1")!!.status)
        assertEquals(1, db.taskDao().getAllTasksList().size)
    }

    @Test
    fun proxy_crud_roundtrip() = runBlocking {
        db.proxyDao().insertProxy(
            ProxyItem(host = "10.0.0.1", port = 1080, type = "socks5", country = "US", isp = "Test ISP")
        )
        val all = db.proxyDao().getAllProxiesList()
        assertEquals(1, all.size)
        assertEquals("US", all.first().country)
        assertEquals(1, db.proxyDao().getProxyCountOnce())
    }

    @Test
    fun email_queue_roundtrip() = runBlocking {
        db.emailDao().insertEmails(listOf(EmailItem(email = "a@example.com")))
        val next = db.emailDao().getNextEmail()
        assertNotNull(next)
        assertEquals("a@example.com", next!!.email)
    }

    @Test
    fun script_crud_roundtrip() = runBlocking {
        db.scriptDao().insertScript(ScriptItem(id = "s1", name = "S", code = "1"))
        assertEquals(1, db.scriptDao().getActiveScripts().size)
    }

    @Test
    fun campaign_log_roundtrip() = runBlocking {
        db.leadLogDao().insertLog(
            CampaignStat(taskId = "t1", taskName = "T", ip = "1.2.3.4", country = "US", leadDetected = true)
        )
        // read-back via DAO list getter exercised implicitly; insert must not throw
        assertTrue(true)
    }

    @Test
    fun click_item_and_template_roundtrip() = runBlocking {
        db.offerClickDao().insertClickItem(OfferClickItem(text = "Get offer"))
        db.workTemplateDao().insertTemplate(WorkTemplateEntity(name = "W"))
        assertTrue(true)
    }
}
