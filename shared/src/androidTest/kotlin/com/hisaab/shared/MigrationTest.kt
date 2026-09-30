package com.hisaab.shared

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.db.Migrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opens every exported schema and migrates it to the current version, validating the result. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = File(instrumentation.targetContext.cacheDir, "migration-test.db").also { it.delete() }

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, file, BundledSQLiteDriver(), HisaabDatabase::class)

    @Test
    fun createsTheCurrentSchemaAndMigratesToLatest() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(HisaabDatabase.VERSION, Migrations.ALL.toList()).close()
    }
}
