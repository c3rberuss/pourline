package dev.josueamaya.pourline.grinder

import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import dev.josueamaya.pourline.settings.GrinderSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Siembra el archivo real de `assets` (el que lleva la app) en una base en memoria. */
class RealCatalogSeedTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var settings: GrinderSettings
  private lateinit var seeder: GrinderCatalogSeeder

  @Before
  fun setUp() {
    context.deleteSharedPreferences(PREFS)
    db = HistoryDatabase.inMemory(context, FakeRecipeTexts())
    settings = GrinderSettings(context, PREFS)
    seeder = GrinderCatalogSeeder(db.grinderDao(), AssetCatalogSource(context), settings, settings)
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteSharedPreferences(PREFS)
  }

  @Test
  fun theRealCatalogIsSeededAsApprovedAndASecondRunIsUpToDate() = runTest {
    val file = GrinderCatalogFile.parse(AssetCatalogSource(context).read()!!).getOrThrow()
    assertEquals(APPROVED_KEYS, file.grinders.map { it.catalogKey })

    assertEquals(SyncResult.Synced(1, added = APPROVED_KEYS.size, updated = 0, retired = 0, deleted = 0), seeder.sync())
    assertEquals(1, settings.catalogVersion)

    val seeded = db.grinderDao().observeAll().first().map { it.toGrinder() }
    assertEquals(APPROVED_KEYS.toSet(), seeded.map { it.catalogKey }.toSet())
    for (expected in file.grinders) {
      val actual = seeded.single { it.catalogKey == expected.catalogKey }
      assertEquals(expected.catalogKey, expected, actual.copy(id = 0))
      assertTrue(expected.catalogKey, !actual.owned && !actual.retired && !actual.sourceName.isNullOrBlank())
    }

    assertEquals(SyncResult.UpToDate, seeder.sync())
  }

  private companion object {
    const val PREFS = "settings_real_catalog_test"
    val APPROVED_KEYS = listOf(
      "timemore-chestnut-c2",
      "timemore-chestnut-c3s",
      "timemore-chestnut-s3",
      "comandante-c40",
      "1zpresso-jx-pro",
      "1zpresso-k-ultra",
      "1zpresso-q2",
      "kingrinder-k6",
      "baratza-encore",
      "fellow-ode-gen-2",
      "femobook-a2",
    )
  }
}
