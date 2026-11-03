# Instant React

App Android nativa (Kotlin) que detecta el momento en que cualquiera de sus **N puntos** (círculos grises) cambia a **azul** y pulsa sobre él automáticamente.
Funciona encima de otras apps con un panel flotante, usando únicamente las API oficiales de Android.

> Objetivo de rendimiento: intentar responder en **< 200 ms**. No se garantiza ninguna velocidad concreta: depende del dispositivo,
> de la tasa de refresco del juego y de lo que tarde Android en inyectar el gesto. La app mide y muestra el retraso configurado
> **por separado** de la latencia real del sistema.

## Puntos configurables y perfiles

* **Cantidad**: 2, 3, 4, 5, 6, 8, 10, 12, 16 o personalizada (1–200). **Por defecto 8 puntos**, ya colocados y calibrados la primera vez que abres la app (siguen la rejilla 3×3 del minijuego, sin el centro). Al cambiar la cantidad se crean/eliminan marcadores automáticamente y se conservan los que ya habías movido.
* **Calibración visual** (◎ Calibrar): marcadores numerados 1, 2, 3… arrastrables, cada uno con su área de detección ajustable (toca un marcador para cambiar solo el suyo). **Añadir / Quitar** puntos, **Auto** (localiza los círculos, también los muy oscuros) y **Verificar**: ✓ verde = el marcador está sobre un círculo, ✗ rojo = revísalo.
* **Detección independiente**: cada punto se analiza por separado, sin suponer ninguna disposición; al ponerse azul cualquiera se identifica qué punto es (se muestra «punto N»), se pulsa su posición y no se repite mientras siga azul.
* **Perfiles**: se crean *Perfil 8 / 6 / 10 puntos* de ejemplo; puedes cambiar, crear, copiar, renombrar y eliminar. Cada perfil guarda sus coordenadas, el tamaño de cada área y el umbral de color. El retraso y la duración del toque son ajustes globales.
* Si venías de la versión 1.0, tu calibración de 6 puntos se conserva como «Perfil anterior (6 puntos)».

## Cómo funciona

| Pieza | API de Android | Qué hace |
|---|---|---|
| Captura | `MediaProjection` + `ImageReader` (RGBA_8888) | Recibe cada fotograma con tu permiso |
| Servicio | `Foreground Service` (`mediaProjection`) | Mantiene la captura activa |
| Panel | `SYSTEM_ALERT_WINDOW` | Ventana flotante movible y minimizable |
| Pulsación | `AccessibilityService.dispatchGesture` | Ejecuta el toque que tú autorizas |
| Detección | Núcleo Kotlin puro (`core/`) | Solo muestrea ~20 píxeles por círculo, nunca la pantalla entera |

Detalles del detector (`app/src/main/java/com/instantreact/core`):

* **Azul por dominancia**: `B − max(R, G)` con umbral escalado por el brillo del píxel + comprobación de matiz. Así el azul se distingue de gris, morado, verde, rojo… y tolera cambios de brillo.
* **Una pulsación por estímulo**: máquina de estados por círculo (armado → disparado → rearmado cuando vuelve a gris). Sin duplicados mientras el círculo sigue azul, y detecta cambios rápidos (azul-gris-azul en fotogramas consecutivos).
* **Resoluciones**: las coordenadas se guardan con la resolución de calibración y se reescalan si cambia (misma orientación). Si la orientación cambia, avisa y pide recalibrar.
* **Latencia**: se mide desde que se recibe el fotograma hasta que se envía el gesto. En JVM el procesado de un fotograma de 2400×1080 con 6 regiones tarda ≈ 1 µs; el coste real lo domina Android (captura + `dispatchGesture`).

## Instalación

1. Descarga `InstantReact-release.apk` (o `-debug.apk`) desde **Actions → último run → Artifacts**, o desde *Releases* si existe.
2. Instálalo (permite "instalar apps desconocidas" para tu navegador/gestor de archivos).

El APK es independiente: no necesita Python, Termux, servidores ni otras apps.

## Primer uso

(Los puntos por defecto ya vienen listos; la calibración solo sirve para afinarlos a tu juego.)

1. Abre **Instant React** y concede:
   * **Ventana flotante** (Mostrar sobre otras apps).
   * **Accesibilidad**: Ajustes → Accesibilidad → Instant React → activar. Se usa *solo* para ejecutar los toques; no lee tu pantalla.
   * **Captura de pantalla**: pulsa *Activar captura y panel flotante* y acepta. En Android 14+ elige **«Toda la pantalla»** (no «una sola app»).
2. Abre tu juego y pulsa **◎ Calibrar** en el panel flotante (o *Calibrar* en la app):
   * Los 8 puntos ya aparecen colocados; pulsa **Auto** para que localice los círculos, o arrastra los marcadores a mano.
   * Ajusta el tamaño del área con **− / +** (debe quedar dentro del círculo).
   * **Guardar**. Recalibra si cambias de resolución o mueves el juego.
3. Elige el **tiempo de reacción** (0, 10, 20, 30, 50, 75, 100, 150, 200 ms o personalizado) y el umbral de azul si hace falta.
4. Pulsa **INICIAR** en el panel. **DETENER** para parar.

Panel flotante: indicador de detección, retraso configurado, último tiempo (reacción y latencia del sistema), contador de pulsaciones, botones Iniciar/Detener/Config/Calibrar, arrastrable y minimizable a un icono. Colócalo donde no tape los círculos.

Estadísticas (en la app): estímulos detectados, pulsaciones, tiempo medio y mejor, detecciones fallidas y % correctas.

## Compilar

Requisitos: JDK 17 y Android SDK 34 (Android Studio los trae).

```bash
./gradlew testDebugUnitTest          # pruebas unitarias (JVM)
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk (firmado)
./gradlew connectedDebugAndroidTest  # pruebas instrumentadas (emulador/dispositivo)
```

O abre la carpeta en Android Studio (Hedgehog o superior).

**Firma**: `keystore/instantreact-release.jks` (contraseña `instantreact`) es una clave de uso personal para instalar el APK *release*.
Si piensas publicar la app, genera tu propia clave y cámbiala en `app/build.gradle.kts`.

## Pruebas

* **Unitarias (JVM, 62 pruebas)**: detección con 2…200 puntos y disposiciones arbitrarias, perfiles (crear, copiar, borrar, persistencia), primer arranque con 8 puntos, círculos muy oscuros, verificación de marcadores, gris→azul en cada punto, cambios rápidos, ausencia de pulsaciones duplicadas, precisión de coordenadas, escalado entre resoluciones, brillo, *stride* de filas, latencia de procesado, estadísticas, autodetección de círculos y textos del panel.
* **Instrumentadas (emulador Android 34 en CI)**: arranque de la app sin cierres, primer arranque con 8 puntos y perfiles persistentes, ventana flotante, fotogramas reales por `ImageReader`, toque real por accesibilidad con coordenadas exactas y cadena completa fotograma → detección → toque en pantalla.
* El diálogo de permiso de `MediaProjection` exige una persona, por lo que la captura real de pantalla hay que probarla a mano en tu móvil.

El workflow `.github/workflows/android.yml` compila, ejecuta las pruebas, genera los APK y los sube como *artifacts*.

## Limitaciones

* Android obliga a aceptar el diálogo de captura cada vez que se inicia el servicio.
* Apps con `FLAG_SECURE` (algunas apps bancarias/DRM) no se pueden capturar.
* `dispatchGesture` añade una latencia propia del sistema que la app no puede eliminar; por eso se muestra separada.
* Los juegos en línea pueden prohibir las pulsaciones automáticas en sus condiciones de uso: úsala solo donde esté permitido.
