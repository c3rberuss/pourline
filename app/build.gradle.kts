import java.io.StringReader
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room)
}

/**
 * Credenciales de la firma de release. Cada valor sale de su variable de entorno (CI) o, si no está,
 * de `keystore.properties` en la raíz del proyecto (local, fuera de git). Sin las cuatro, el release
 * se genera sin firmar.
 */
val keystoreProperties = Properties().apply {
  providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText.orNull?.let { load(StringReader(it)) }
}

fun signingValue(property: String, env: String): String? =
  (providers.environmentVariable(env).orNull ?: keystoreProperties.getProperty(property))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "POURLINE_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "POURLINE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "POURLINE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "POURLINE_KEY_PASSWORD")
val hasReleaseSigning =
  listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

/**
 * Copia `catalog/recipes.json` (la única fuente del catálogo de recetas, SPEC-012) a `assets/catalog/recipes.json`
 * del APK. Nunca se mantiene una segunda copia a mano.
 */
abstract class CopyRecipeCatalogTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val catalogFile: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun copy() {
    val target = outputDir.get().asFile.resolve("catalog/recipes.json")
    target.parentFile.mkdirs()
    catalogFile.get().asFile.copyTo(target, overwrite = true)
  }
}

val copyRecipeCatalog = tasks.register<CopyRecipeCatalogTask>("copyRecipeCatalog") {
  catalogFile.set(rootProject.layout.projectDirectory.file("catalog/recipes.json"))
  outputDir.set(layout.buildDirectory.dir("generated/recipeCatalog"))
}

android {
  namespace = "dev.josueamaya.pourline"
  compileSdk = 37
  defaultConfig {
    applicationId = "dev.josueamaya.pourline"
    minSdk = 26
    targetSdk = 37
    // El workflow de release las pasa desde el tag (vX.Y.Z); en local se usan estos valores.
    versionCode = providers.gradleProperty("pourline.versionCode").orNull?.toInt() ?: 1
    versionName = providers.gradleProperty("pourline.versionName").orNull ?: "1.0"
    testInstrumentationRunner = "dev.josueamaya.pourline.PourlineTestRunner"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      // R8: reduce, optimiza y ofusca el código y quita los recursos que no se usan.
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  testOptions {
    // Los tests JVM del ViewModel llaman a `Log`; los métodos de Android devuelven valores por defecto.
    unitTests.isReturnDefaultValues = true
    // `-PlabelsExpected=<ruta>` activa la prueba de aciertos con las etiquetas reales (F4); sin ella se salta.
    unitTests.all { it.systemProperty("pourline.labelsExpected", providers.gradleProperty("labelsExpected").getOrElse("")) }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

androidComponents {
  onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copyRecipeCatalog, CopyRecipeCatalogTask::outputDir)
  }
}

kotlin {
  jvmToolchain(17)
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(project(":protocol"))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.kotlinx.coroutines.test)

  // Catálogo de molinos (assets/grinders/catalog.json); también lee los fixtures JSON del OCR en los tests JVM
  implementation(libs.kotlinx.serialization.json)

  // Room (historial de preparaciones)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  androidTestImplementation(libs.androidx.room.testing)
  // `room-migration` (de room-testing) necesita kotlinx-serialization 1.8+, pero el classpath de debug
  // resuelve 1.7.3 por lifecycle y AGP alinea androidTest con él. Solo afecta a debug, no a release.
  debugImplementation(libs.kotlinx.serialization.core)

  // OCR de las etiquetas: reconocimiento de texto latino con el modelo incluido en el APK (sin red)
  implementation(libs.mlkit.text.recognition)

  // Compartir recetas con QR (SPEC-009): zxing genera la matriz (Java puro); la variante de Play Services de
  // barcode-scanning lee QR de imágenes y descarga su modelo Play Services (no va en el APK); el Code Scanner lo
  // ejecuta Google Play Services y no pide el permiso CAMERA
  implementation(libs.zxing.core)
  implementation(libs.play.services.mlkit.barcode.scanning)
  implementation(libs.play.services.code.scanner)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
