package dev.josueamaya.pourline.settings

import dev.josueamaya.pourline.settings.FreeBrewDefaults.Companion.DEFAULT_RATIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exportar y restaurar los ajustes de una copia (SPEC-011 §3.2 y §3.5), sin Android: con `FakeSharedPreferences`. */
class SettingsSnapshotTest {

  private val prefs = FakeSharedPreferences()
  private val theme = ThemeSettings(prefs)
  private val experience = ExperienceSettings(prefs)
  private val connection = ConnectionSettings(prefs)
  private val brew = BrewSettings(prefs)
  private val target = TargetSettings(prefs)
  private val grinder = GrinderSettings(prefs)
  private val all: List<BackupableSettings> = listOf(theme, experience, connection, brew, target, grinder)

  private fun export(): SettingsSnapshot = all.fold(SettingsSnapshot()) { snapshot, settings -> settings.exportInto(snapshot) }

  private fun restore(snapshot: SettingsSnapshot) = all.forEach { it.restoreFrom(snapshot) }

  private val full = SettingsSnapshot(
    themeMode = "DARK",
    experienceMode = "EXPERT",
    onboardingDone = true,
    useScale = false,
    lastScaleAddress = "AA:BB:CC:DD:EE:FF",
    lastScaleName = "TIMEMORE DOT",
    autoFinishSeconds = 90,
    countdownSeconds = 5,
    lastCoffeeId = 12,
    defaultDoseG = 18.5f,
    defaultRatio = 15.5f,
    targetRefType = "BREW",
    targetRefId = 77,
    stageVibration = false,
    stageSound = false,
    defaultGrinderId = 4,
    grinderCatalogVersion = 2,
  )

  @Test
  fun `each class exports only its present keys with their stored values`() {
    assertEquals(SettingsSnapshot(), export())

    prefs.values["theme_mode"] = "LIGHT"
    prefs.values["auto_finish_seconds"] = 7 // fuera de rango: se exporta tal cual
    prefs.values["last_coffee_id"] = 3L
    prefs.values["default_dose_g"] = 15.3f
    prefs.values["tour_seen"] = setOf("a", "b")
    prefs.values["clave_ajena"] = "x"

    val partial = export()
    assertEquals(SettingsSnapshot(themeMode = "LIGHT", autoFinishSeconds = 7, lastCoffeeId = 3, defaultDoseG = 15.3f), partial)
    // Cada clase solo aporta las suyas.
    assertEquals(SettingsSnapshot(themeMode = "LIGHT"), theme.exportInto(SettingsSnapshot()))
    assertEquals(SettingsSnapshot(), experience.exportInto(SettingsSnapshot()))
    assertEquals(SettingsSnapshot(autoFinishSeconds = 7, lastCoffeeId = 3, defaultDoseG = 15.3f), brew.exportInto(SettingsSnapshot()))
    // Un tipo inesperado cuenta como ausente.
    prefs.values["stage_sound"] = "no"
    prefs.values["default_grinder_id"] = "cinco"
    assertNull(export().stageSound)
    assertNull(export().defaultGrinderId)
  }

  @Test
  fun `restoring writes the present keys, removes the absent ones and updates the flows`() {
    // Estado previo distinto de la copia, con claves que la copia no trae.
    prefs.values["theme_mode"] = "LIGHT"
    prefs.values["last_scale_address"] = "11:22"
    prefs.values["last_scale_name"] = "Vieja"
    prefs.values["target_ref_id"] = 1L
    prefs.values["target_ref_type"] = "RECIPE"
    prefs.values["default_grinder_id"] = 9L
    prefs.values["tour_seen"] = setOf("x")
    val tourBefore = prefs.values["tour_seen"]
    val freshTheme = ThemeSettings(prefs)
    val freshBrew = BrewSettings(prefs)
    val freshTarget = TargetSettings(prefs)
    val freshGrinder = GrinderSettings(prefs)
    val freshConnection = ConnectionSettings(prefs)

    // Todas las claves presentes: valores nuevos en las prefs y en los flujos de las mismas instancias.
    listOf(freshTheme, experience, freshConnection, freshBrew, freshTarget, freshGrinder).forEach { it.restoreFrom(full) }
    assertEquals(full, listOf(freshTheme, experience, freshConnection, freshBrew, freshTarget, freshGrinder).fold(SettingsSnapshot()) { s, c -> c.exportInto(s) })
    assertEquals(ThemeMode.DARK, freshTheme.mode.value)
    assertEquals(ExperienceMode.EXPERT, experience.mode.value)
    assertTrue(experience.onboardingDone.value)
    assertEquals(90, freshBrew.autoFinishSeconds.value)
    assertEquals(5, freshBrew.countdownSeconds.value)
    assertEquals(12L, freshBrew.lastCoffeeId.value)
    assertEquals(18.5, freshBrew.defaultDoseG.value, 0.0)
    assertEquals(15.5, freshBrew.defaultRatio.value, 0.0)
    assertEquals(ReferenceKey.Brew(77), freshTarget.reference.value)
    assertFalse(freshTarget.stageVibration.value)
    assertFalse(freshTarget.stageSound.value)
    assertEquals(4L, freshGrinder.defaultGrinderId.value)
    assertEquals(2, freshGrinder.catalogVersion)
    assertEquals("TIMEMORE DOT", freshConnection.lastScale.value?.name)
    assertFalse(freshConnection.useScale.value)
    assertTrue(freshConnection.scaleModeChosen.value)

    // Una copia con pocas claves borra las ausentes y los flujos vuelven al valor por defecto.
    listOf(freshTheme, experience, freshConnection, freshBrew, freshTarget, freshGrinder).forEach { it.restoreFrom(SettingsSnapshot(themeMode = "LIGHT")) }
    assertEquals(setOf("theme_mode", "tour_seen"), prefs.values.keys)
    assertEquals(ThemeMode.LIGHT, freshTheme.mode.value)
    assertEquals(ExperienceMode.BASIC, experience.mode.value)
    assertFalse(experience.onboardingDone.value)
    assertEquals(60, freshBrew.autoFinishSeconds.value)
    assertNull(freshBrew.lastCoffeeId.value)
    assertEquals(DEFAULT_RATIO, freshBrew.defaultRatio.value, 0.0)
    assertNull(freshTarget.reference.value)
    assertTrue(freshTarget.stageVibration.value)
    assertNull(freshGrinder.defaultGrinderId.value)
    assertEquals(0, freshGrinder.catalogVersion)
    assertNull(freshConnection.lastScale.value)
    assertEquals(tourBefore, prefs.values["tour_seen"])
  }

  @Test
  fun `restoring an out of range or unknown value falls back like a cold read without throwing`() {
    restore(
      SettingsSnapshot(
        autoFinishSeconds = 7,
        countdownSeconds = 4,
        themeMode = "PURPLE",
        experienceMode = "GURU",
        defaultRatio = 99f,
        defaultDoseG = 1f,
        targetRefType = "OTHER",
        targetRefId = 5,
      ),
    )
    assertEquals(60, brew.autoFinishSeconds.value)
    assertEquals(BrewSettings.DEFAULT_COUNTDOWN_SECONDS, brew.countdownSeconds.value)
    assertEquals(ThemeMode.SYSTEM, theme.mode.value)
    assertEquals(ExperienceMode.BASIC, experience.mode.value)
    assertEquals(16.0, brew.defaultRatio.value, 0.0)
    assertEquals(15.0, brew.defaultDoseG.value, 0.0)
    assertNull(target.reference.value)
    // Lo mismo que lee una instancia nueva sobre las mismas prefs.
    assertEquals(60, BrewSettings(prefs).autoFinishSeconds.value)
    assertEquals(ThemeMode.SYSTEM, ThemeSettings(prefs).mode.value)
  }

  @Test
  fun `restoring the scale keys recomputes the derived flows`() {
    // Sin `use_scale` y con báscula recordada: se usa báscula y el modo no está elegido.
    restore(SettingsSnapshot(lastScaleAddress = "AA:BB", lastScaleName = "Mi DOT"))
    assertTrue(connection.useScale.value)
    assertFalse(connection.scaleModeChosen.value)
    assertEquals("Mi DOT", connection.lastScale.value?.name)

    // Sin nombre, el nombre es la dirección (como en la lectura en frío).
    restore(SettingsSnapshot(lastScaleAddress = "AA:BB"))
    assertEquals("AA:BB", connection.lastScale.value?.name)

    // Con `use_scale = false` explícito: el modo está elegido.
    restore(SettingsSnapshot(useScale = false, lastScaleAddress = "AA:BB"))
    assertFalse(connection.useScale.value)
    assertTrue(connection.scaleModeChosen.value)

    // Sin nada: ni báscula ni modo elegido.
    restore(SettingsSnapshot())
    assertFalse(connection.useScale.value)
    assertFalse(connection.scaleModeChosen.value)
    assertNull(connection.lastScale.value)
  }

  @Test
  fun `restoring never touches tour_seen`() {
    val tour = TourSettings(prefs)
    tour.markSeen(listOf("prepare.settings", "prepare.start"))
    restore(full)
    assertEquals(setOf("prepare.settings", "prepare.start"), tour.seen.value)
    assertEquals(setOf("prepare.settings", "prepare.start"), prefs.values["tour_seen"])
    assertTrue("el snapshot no tiene campo de recorrido", SettingsSnapshot::class.java.declaredFields.none { it.name.contains("tour", ignoreCase = true) })
    restore(SettingsSnapshot())
    assertEquals(setOf("prepare.settings", "prepare.start"), prefs.values["tour_seen"])
  }

  @Test
  fun `each class restores in a single write`() {
    all.forEach { settings ->
      val before = prefs.writes
      settings.restoreFrom(full)
      assertEquals(settings::class.simpleName, before + 1, prefs.writes)
    }
  }
}
