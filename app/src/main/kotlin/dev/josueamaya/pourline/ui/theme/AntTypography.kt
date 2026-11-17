package dev.josueamaya.pourline.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/** Escala tipográfica de Ant Design v5 (`fontSize` 14 y `fontSizeHeading1..5`, con sus line-heights). */
@Immutable
data class AntTypography(
  val textSM: TextStyle,
  val text: TextStyle,
  val textLG: TextStyle,
  val textXL: TextStyle,
  val heading1: TextStyle,
  val heading2: TextStyle,
  val heading3: TextStyle,
  val heading4: TextStyle,
  val heading5: TextStyle,
)

fun antTypography(fontFamily: FontFamily = FontFamily.Default): AntTypography {
  fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = fontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
  )
  return AntTypography(
    textSM = style(12, 20),
    text = style(14, 22),
    textLG = style(16, 24),
    textXL = style(20, 28),
    heading1 = style(38, 46, FontWeight.SemiBold),
    heading2 = style(30, 38, FontWeight.SemiBold),
    heading3 = style(24, 32, FontWeight.SemiBold),
    heading4 = style(20, 28, FontWeight.SemiBold),
    heading5 = style(16, 24, FontWeight.SemiBold),
  )
}
