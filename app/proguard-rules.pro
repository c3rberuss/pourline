# Reglas de R8 para Pourline (build de release).
#
# Room, kotlinx.serialization, Navigation 3, ML Kit, Play Services y Compose traen sus propias reglas
# (consumer rules), así que aquí solo va lo específico de la app.

# Trazas de error legibles: conserva números de línea y oculta el nombre del archivo original.
# Para desofuscarlas, usa app/build/outputs/mapping/release/mapping.txt con `retrace`.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit (OCR de etiquetas y lectura de QR) crea sus registradores por reflexión con el constructor vacío.
# Sus reglas conservan las clases pero no el constructor, y R8 (modo completo) lo elimina; sin él, logcat
# muestra `NoSuchMethodException: ...Registrar.<init> []` y los reconocedores no se inicializan.
-keep class com.google.mlkit.**.*Registrar { public <init>(); }
