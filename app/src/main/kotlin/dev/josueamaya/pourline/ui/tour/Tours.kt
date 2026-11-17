package dev.josueamaya.pourline.ui.tour

import dev.josueamaya.pourline.R

/**
 * El catálogo de recorridos guiados (SPEC-008, §3.7 y §2.6 del plan): uno por pantalla, con sus pasos en el orden en
 * que se presentan. Cada paso se llama `"<recorrido>.<paso>"` y sus textos, `tour_<recorrido>_<paso>_title` y
 * `tour_<recorrido>_<paso>_body`. Las anclas son los `testTag` de los elementos que se resaltan.
 */
object Tours {

  /** General, en Preparar: la pantalla y las cuatro pestañas. */
  val prepare = Tour(
    "prepare",
    listOf(
      TourStep("prepare.welcome", null, R.string.tour_prepare_welcome_title, R.string.tour_prepare_welcome_body),
      TourStep("prepare.scale", "scale_card", R.string.tour_prepare_scale_title, R.string.tour_prepare_scale_body),
      TourStep("prepare.recipe", "prepare_recipe", R.string.tour_prepare_recipe_title, R.string.tour_prepare_recipe_body),
      TourStep("prepare.coffee", "prepare_coffee", R.string.tour_prepare_coffee_title, R.string.tour_prepare_coffee_body),
      TourStep("prepare.dose", "prepare_dose_card", R.string.tour_prepare_dose_title, R.string.tour_prepare_dose_body),
      TourStep("prepare.grind", "grind_card", R.string.tour_prepare_grind_title, R.string.tour_prepare_grind_body),
      TourStep("prepare.grind_ruler", "grind_card_ruler", R.string.tour_prepare_grind_ruler_title, R.string.tour_prepare_grind_ruler_body, expertOnly = true),
      TourStep("prepare.start", "prepare_start", R.string.tour_prepare_start_title, R.string.tour_prepare_start_body),
      TourStep("prepare.start_scaleless", "prepare_start_scaleless", R.string.tour_prepare_start_scaleless_title, R.string.tour_prepare_start_scaleless_body),
      TourStep("prepare.settings", "prepare_settings", R.string.tour_prepare_settings_title, R.string.tour_prepare_settings_body),
      TourStep("prepare.tab_coffees", "tab_coffees", R.string.tour_prepare_tab_coffees_title, R.string.tour_prepare_tab_coffees_body),
      TourStep("prepare.tab_recipes", "tab_recipes", R.string.tour_prepare_tab_recipes_title, R.string.tour_prepare_tab_recipes_body),
      TourStep("prepare.tab_history", "tab_history", R.string.tour_prepare_tab_history_title, R.string.tour_prepare_tab_history_body),
      TourStep("prepare.tab_tools", "tab_tools", R.string.tour_prepare_tab_tools_title, R.string.tour_prepare_tab_tools_body),
    ),
  )

  /** Sesión con báscula, en los pasos previos al vertido. */
  val sessionPrep = Tour(
    "session_prep",
    listOf(
      TourStep("session_prep.stepper", "prep_stepper", R.string.tour_session_prep_stepper_title, R.string.tour_session_prep_stepper_body),
      TourStep("session_prep.weight", "prep_weight", R.string.tour_session_prep_weight_title, R.string.tour_session_prep_weight_body),
      TourStep("session_prep.actions", "prep_tare", R.string.tour_session_prep_actions_title, R.string.tour_session_prep_actions_body),
      TourStep("session_prep.timer", null, R.string.tour_session_prep_timer_title, R.string.tour_session_prep_timer_body),
      TourStep("session_prep.options", "session_options", R.string.tour_session_prep_options_title, R.string.tour_session_prep_options_body),
      TourStep("session_prep.close", "session_close", R.string.tour_session_prep_close_title, R.string.tour_session_prep_close_body),
    ),
  )

  /** Sesión sin báscula, en el estado listo. */
  val sessionScaleless = Tour(
    "session_scaleless",
    listOf(
      TourStep("session_scaleless.plan", "scaleless_plan_water", R.string.tour_session_scaleless_plan_title, R.string.tour_session_scaleless_plan_body),
      TourStep("session_scaleless.start", "scaleless_start", R.string.tour_session_scaleless_start_title, R.string.tour_session_scaleless_start_body),
      TourStep("session_scaleless.dial", null, R.string.tour_session_scaleless_dial_title, R.string.tour_session_scaleless_dial_body),
      TourStep("session_scaleless.close", "session_close", R.string.tour_session_scaleless_close_title, R.string.tour_session_scaleless_close_body),
    ),
  )

  /** Resumen, al terminar una preparación. */
  val summary = Tour(
    "summary",
    listOf(
      TourStep("summary.rating", "summary_details", R.string.tour_summary_rating_title, R.string.tour_summary_rating_body),
      TourStep("summary.details", "summary_details", R.string.tour_summary_details_title, R.string.tour_summary_details_body),
      TourStep("summary.share", "summary_share", R.string.tour_summary_share_title, R.string.tour_summary_share_body),
      TourStep("summary.expert", "summary_expert", R.string.tour_summary_expert_title, R.string.tour_summary_expert_body, expertOnly = true),
    ),
  )

  /** Cafés. */
  val coffees = Tour(
    "coffees",
    listOf(
      TourStep("coffees.add", "coffees_add", R.string.tour_coffees_add_title, R.string.tour_coffees_add_body),
      TourStep("coffees.search", "coffees_search", R.string.tour_coffees_search_title, R.string.tour_coffees_search_body),
      TourStep("coffees.tabs", "coffees_tabs", R.string.tour_coffees_tabs_title, R.string.tour_coffees_tabs_body),
      TourStep("coffees.row", "coffee_row", R.string.tour_coffees_row_title, R.string.tour_coffees_row_body),
    ),
  )

  /** Alta y edición de un café. */
  val coffeeEdit = Tour(
    "coffee_edit",
    listOf(
      TourStep("coffee_edit.photos", "coffee_photo_slot_1", R.string.tour_coffee_edit_photos_title, R.string.tour_coffee_edit_photos_body),
      TourStep("coffee_edit.read_label", "coffee_edit_read_label", R.string.tour_coffee_edit_read_label_title, R.string.tour_coffee_edit_read_label_body),
      TourStep("coffee_edit.save", "coffee_edit_save", R.string.tour_coffee_edit_save_title, R.string.tour_coffee_edit_save_body),
    ),
  )

  /** Detalle de un café. */
  val coffeeDetail = Tour(
    "coffee_detail",
    listOf(
      TourStep("coffee_detail.status", "coffee_detail_status", R.string.tour_coffee_detail_status_title, R.string.tour_coffee_detail_status_body),
      TourStep("coffee_detail.log", "coffee_detail_log", R.string.tour_coffee_detail_log_title, R.string.tour_coffee_detail_log_body),
      TourStep("coffee_detail.new_bag", "coffee_detail_new_bag", R.string.tour_coffee_detail_new_bag_title, R.string.tour_coffee_detail_new_bag_body),
      TourStep("coffee_detail.share", "coffee_detail_share", R.string.tour_coffee_detail_share_title, R.string.tour_coffee_detail_share_body),
    ),
  )

  /** Recetas. */
  val recipes = Tour(
    "recipes",
    listOf(
      TourStep("recipes.builtin", "recipe_builtin_tag", R.string.tour_recipes_builtin_title, R.string.tour_recipes_builtin_body),
      TourStep("recipes.active", "recipe_active_tag", R.string.tour_recipes_active_title, R.string.tour_recipes_active_body),
      TourStep("recipes.add", "recipes_add", R.string.tour_recipes_add_title, R.string.tour_recipes_add_body),
    ),
  )

  /** Editar una receta. */
  val recipeEdit = Tour(
    "recipe_edit",
    listOf(
      TourStep("recipe_edit.method", "recipe_edit_method", R.string.tour_recipe_edit_method_title, R.string.tour_recipe_edit_method_body),
      TourStep("recipe_edit.stage", "recipe_stage_0", R.string.tour_recipe_edit_stage_title, R.string.tour_recipe_edit_stage_body),
      TourStep("recipe_edit.add_stage", "recipe_add_stage", R.string.tour_recipe_edit_add_stage_title, R.string.tour_recipe_edit_add_stage_body),
      TourStep("recipe_edit.totals", "recipe_edit_totals", R.string.tour_recipe_edit_totals_title, R.string.tour_recipe_edit_totals_body),
      TourStep("recipe_edit.preview", "recipe_edit_preview", R.string.tour_recipe_edit_preview_title, R.string.tour_recipe_edit_preview_body, expertOnly = true),
    ),
  )

  /** Historial. */
  val history = Tour(
    "history",
    listOf(
      TourStep("history.filters", "history_filter_coffee", R.string.tour_history_filters_title, R.string.tour_history_filters_body),
      TourStep("history.progress", "history_progress", R.string.tour_history_progress_title, R.string.tour_history_progress_body, expertOnly = true),
      TourStep("history.row", "history_row", R.string.tour_history_row_title, R.string.tour_history_row_body),
      TourStep("history.scaleless", "history_row_scaleless", R.string.tour_history_scaleless_title, R.string.tour_history_scaleless_body),
    ),
  )

  /** Detalle de una preparación. */
  val brewDetail = Tour(
    "brew_detail",
    listOf(
      TourStep("brew_detail.chart", "brew_detail_chart", R.string.tour_brew_detail_chart_title, R.string.tour_brew_detail_chart_body, expertOnly = true),
      TourStep("brew_detail.details", "brew_detail_details", R.string.tour_brew_detail_details_title, R.string.tour_brew_detail_details_body),
      TourStep("brew_detail.edit", "brew_detail_edit", R.string.tour_brew_detail_edit_title, R.string.tour_brew_detail_edit_body),
      TourStep("brew_detail.compare", "brew_detail_compare", R.string.tour_brew_detail_compare_title, R.string.tour_brew_detail_compare_body, expertOnly = true),
      TourStep("brew_detail.save_recipe", "brew_detail_save_recipe", R.string.tour_brew_detail_save_recipe_title, R.string.tour_brew_detail_save_recipe_body, expertOnly = true),
      TourStep("brew_detail.share", "brew_detail_share", R.string.tour_brew_detail_share_title, R.string.tour_brew_detail_share_body),
    ),
  )

  /** Comparar dos preparaciones. */
  val compare = Tour(
    "compare",
    listOf(
      TourStep("compare.chart", "compare_chart_water", R.string.tour_compare_chart_title, R.string.tour_compare_chart_body),
      TourStep("compare.legend", "compare_legend", R.string.tour_compare_legend_title, R.string.tour_compare_legend_body),
      TourStep("compare.table", "compare_table", R.string.tour_compare_table_title, R.string.tour_compare_table_body),
    ),
  )

  /** Herramientas. */
  val tools = Tour(
    "tools",
    listOf(
      TourStep("tools.ratio", "tools_ratio", R.string.tour_tools_ratio_title, R.string.tour_tools_ratio_body),
      TourStep("tools.four_six", "tools_four_six", R.string.tour_tools_four_six_title, R.string.tour_tools_four_six_body),
      TourStep("tools.converter", "tools_converter", R.string.tour_tools_converter_title, R.string.tour_tools_converter_body),
    ),
  )

  /** Calculadora de ratio. */
  val ratioCalculator = Tour(
    "ratio_calculator",
    listOf(
      TourStep("ratio_calculator.fields", "ratio_dose", R.string.tour_ratio_calculator_fields_title, R.string.tour_ratio_calculator_fields_body),
      TourStep("ratio_calculator.validation", "ratio_water", R.string.tour_ratio_calculator_validation_title, R.string.tour_ratio_calculator_validation_body),
    ),
  )

  /** Generador 4:6. */
  val fourSix = Tour(
    "four_six",
    listOf(
      TourStep("four_six.dose", "four_six_dose", R.string.tour_four_six_dose_title, R.string.tour_four_six_dose_body),
      TourStep("four_six.sliders", "four_six_levels", R.string.tour_four_six_sliders_title, R.string.tour_four_six_sliders_body),
      TourStep("four_six.pours", "four_six_pour_0", R.string.tour_four_six_pours_title, R.string.tour_four_six_pours_body),
      TourStep("four_six.save", "four_six_save", R.string.tour_four_six_save_title, R.string.tour_four_six_save_body),
    ),
  )

  /** Convertidor de molienda. */
  val converter = Tour(
    "converter",
    listOf(
      TourStep("converter.source", "converter_source", R.string.tour_converter_source_title, R.string.tour_converter_source_body),
      TourStep("converter.rows", "converter_row", R.string.tour_converter_rows_title, R.string.tour_converter_rows_body),
    ),
  )

  /** Ajustes. */
  val settings = Tour(
    "settings",
    listOf(
      TourStep("settings.mode", "settings_mode", R.string.tour_settings_mode_title, R.string.tour_settings_mode_body),
      TourStep("settings.grinders", "settings_grinders", R.string.tour_settings_grinders_title, R.string.tour_settings_grinders_body),
      TourStep("settings.theme", "settings_theme", R.string.tour_settings_theme_title, R.string.tour_settings_theme_body),
      TourStep("settings.alerts", "settings_stage_vibration", R.string.tour_settings_alerts_title, R.string.tour_settings_alerts_body),
      TourStep("settings.countdown", "settings_countdown", R.string.tour_settings_countdown_title, R.string.tour_settings_countdown_body),
      TourStep("settings.replay", "settings_tour_replay", R.string.tour_settings_replay_title, R.string.tour_settings_replay_body),
    ),
  )

  /** Todos los recorridos, en el orden del spec. */
  val all: List<Tour> = listOf(prepare, sessionPrep, sessionScaleless, summary, coffees, coffeeEdit, coffeeDetail, recipes, recipeEdit, history, brewDetail, compare, tools, ratioCalculator, fourSix, converter, settings)

  /** Los ids de todos los pasos: lo que marca como visto `skipTours()` en los tests. */
  val allStepIds: Set<String> = all.flatMap { tour -> tour.steps.map { it.id } }.toSet()
}
