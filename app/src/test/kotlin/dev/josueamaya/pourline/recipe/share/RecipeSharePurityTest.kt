package dev.josueamaya.pourline.recipe.share

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecipeSharePurityTest {
  @Test
  fun `the recipe share package does not import android`() {
    val dir = File("src/main/kotlin/dev/josueamaya/pourline/recipe/share")
    val files = dir.listFiles { file -> file.extension == "kt" }.orEmpty()
    assertTrue("no se encontró el paquete en ${dir.absolutePath}", files.isNotEmpty())
    for (file in files) {
      val offending = file.readLines().filter { it.startsWith("import android") || it.startsWith("import androidx") }
      assertTrue("${file.name} importa Android: $offending", offending.isEmpty())
    }
  }
}
