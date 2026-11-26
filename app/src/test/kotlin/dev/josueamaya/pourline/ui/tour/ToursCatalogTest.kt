package dev.josueamaya.pourline.ui.tour

import dev.josueamaya.pourline.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ToursCatalogTest {

  private val steps = Tours.all.flatMap { it.steps }

  /** Id de recurso -> nombre, por reflexión sobre `R.string` (el tipo y el valor no se pueden comparar de otro modo en la JVM). */
  private val stringNames: Map<Int, String> =
    R.string::class.java.fields.associate { it.getInt(null) to it.name }

  private fun resourceNames(path: String): Map<String, String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
    val nodes = doc.getElementsByTagName("string")
    return (0 until nodes.length).associate { i ->
      val node = nodes.item(i)
      node.attributes.getNamedItem("name").nodeValue to node.textContent
    }
  }

  private val es by lazy { resourceNames("src/main/res/values/strings.xml") }
  private val en by lazy { resourceNames("src/main/res/values-en/strings.xml") }

  @Test
  fun `step ids are unique across all tours`() {
    assertEquals(steps.size, steps.map { it.id }.toSet().size)
    assertEquals(steps.map { it.id }.toSet(), Tours.allStepIds)
  }

  @Test
  fun `every step id starts with its tour id`() {
    Tours.all.forEach { tour ->
      tour.steps.forEach { step -> assertTrue("${step.id} in ${tour.id}", step.id.startsWith(tour.id + ".")) }
    }
  }

  @Test
  fun `there are the seventeen tours of the spec in order`() {
    val expected = listOf(
      "prepare" to 14, "session_prep" to 6, "session_scaleless" to 4, "summary" to 4, "coffees" to 4, "coffee_edit" to 3,
      "coffee_detail" to 4, "recipes" to 2, "recipe_edit" to 5, "history" to 4, "brew_detail" to 6, "compare" to 3,
      "tools" to 3, "ratio_calculator" to 2, "four_six" to 4, "converter" to 2, "settings" to 7,
    )

    assertEquals(expected, Tours.all.map { it.id to it.steps.size })
    assertEquals(77, steps.size)
  }

  @Test
  fun `every step points to a title and a body named after its id`() {
    steps.forEach { step ->
      val base = "tour_" + step.id.replace('.', '_')
      assertEquals("title of ${step.id}", base + "_title", stringNames[step.title])
      assertEquals("body of ${step.id}", base + "_body", stringNames[step.body])
    }
  }

  @Test
  fun `every resource exists in values and values-en`() {
    val common = listOf(
      "tour_next", "tour_prev", "tour_done", "tour_skip", "tour_counter", "tour_skipped_hint", "settings_tour_replay",
      "settings_tour_replay_hint", "tour_decline", "tour_accept",
    )
    val names = common + steps.flatMap { step -> listOf(stringNames.getValue(step.title), stringNames.getValue(step.body)) }

    names.forEach { name ->
      assertTrue("$name in values", !es[name].isNullOrBlank())
      assertTrue("$name in values-en", !en[name].isNullOrBlank())
    }
  }

  @Test
  fun `bodies have at most two sentences and no emoji`() {
    steps.forEach { step ->
      val name = stringNames.getValue(step.body)
      listOf("es" to es, "en" to en).forEach { (lang, strings) ->
        val text = strings.getValue(name)
        // Una frase termina en punto o en admiración. Una pregunta corta dentro del cuerpo ("¿Sin báscula a mano?") cuenta
        // con la frase que la sigue, así que no se cuenta aparte.
        val sentences = Regex("[.!](\\s|$)").findAll(text).count()
        assertTrue("$name ($lang) has $sentences sentences", sentences <= 2)
        assertFalse("$name ($lang) has an emoji", text.codePoints().anyMatch { it in 0x1F000..0x1FAFF || it in 0x2600..0x27BF || it == 0xFE0F })
      }
    }
  }

  @Test
  fun `expert only steps match the spec`() {
    val expert = steps.filter { it.expertOnly }.map { it.id }

    assertEquals(
      setOf(
        "prepare.grind_ruler", "summary.expert", "recipe_edit.preview", "history.progress", "brew_detail.chart",
        "brew_detail.compare", "brew_detail.save_recipe",
      ),
      expert.toSet(),
    )
    assertEquals(7, expert.size)
  }

  @Test
  fun `every tour has at least one anchored step`() {
    Tours.all.forEach { tour -> assertTrue(tour.id, tour.steps.any { it.anchor != null }) }
  }
}
