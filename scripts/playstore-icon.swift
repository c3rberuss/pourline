// Renderiza el icono de Google Play (512×512, PNG de 32 bits, sRGB) a partir de las mismas formas que
// ic_launcher_background.xml + ic_launcher_foreground.xml. Se toma la zona visible del adaptive icon
// (los 72 dp centrales del lienzo de 108 dp); Google Play aplica él mismo las esquinas redondeadas.
//
// Si cambian las capas del icono, actualiza aquí las formas y regenera desde la raíz del repo:
//   swift scripts/playstore-icon.swift [salida.png]
// Por defecto escribe app/src/main/ic_launcher-playstore.png (solo macOS: usa Core Graphics).
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let size = 512
let out = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "app/src/main/ic_launcher-playstore.png"
let srgb = CGColorSpace(name: CGColorSpace.sRGB)!
let ctx = CGContext(
  data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
  space: srgb, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
)!

// Coordenadas del VectorDrawable (y hacia abajo, viewport 108) → píxeles.
let scale = CGFloat(size) / 72.0
ctx.translateBy(x: 0, y: CGFloat(size))
ctx.scaleBy(x: scale, y: -scale)
ctx.translateBy(x: -18, y: -18)
ctx.setShouldAntialias(true)
ctx.interpolationQuality = .high

func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
  CGColor(
    colorSpace: srgb,
    components: [
      CGFloat((hex >> 16) & 0xFF) / 255, CGFloat((hex >> 8) & 0xFF) / 255, CGFloat(hex & 0xFF) / 255, alpha,
    ])!
}

// Fondo: M0,0h108v108h-108z, #1668E8
ctx.setFillColor(color(0x1668E8))
ctx.fill(CGRect(x: 0, y: 0, width: 108, height: 108))

// Área: M32,76V70H39L45,57H54L60,45H68L74,36H76V76Z, blanco al 28 %
let area = CGMutablePath()
area.addLines(between: [
  CGPoint(x: 32, y: 76), CGPoint(x: 32, y: 70), CGPoint(x: 39, y: 70), CGPoint(x: 45, y: 57),
  CGPoint(x: 54, y: 57), CGPoint(x: 60, y: 45), CGPoint(x: 68, y: 45), CGPoint(x: 74, y: 36),
  CGPoint(x: 76, y: 36), CGPoint(x: 76, y: 76),
])
area.closeSubpath()
ctx.addPath(area)
ctx.setFillColor(color(0xFFFFFF, 0.28))
ctx.fillPath()

// Línea: M32,70H39L45,57H54L60,45H68L74,36, trazo 5, extremos y uniones redondeados
let line = CGMutablePath()
line.addLines(between: [
  CGPoint(x: 32, y: 70), CGPoint(x: 39, y: 70), CGPoint(x: 45, y: 57), CGPoint(x: 54, y: 57),
  CGPoint(x: 60, y: 45), CGPoint(x: 68, y: 45), CGPoint(x: 74, y: 36),
])
ctx.addPath(line)
ctx.setStrokeColor(color(0xFFFFFF))
ctx.setLineWidth(5)
ctx.setLineCap(.round)
ctx.setLineJoin(.round)
ctx.strokePath()

let image = ctx.makeImage()!
let dest = CGImageDestinationCreateWithURL(
  URL(fileURLWithPath: out) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(dest, image, nil)
guard CGImageDestinationFinalize(dest) else { fatalError("No se pudo escribir \(out)") }
print("OK \(out)")
