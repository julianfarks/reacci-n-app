# Instant React

App Android nativa (Kotlin) que detecta el momento en que un círculo gris cambia a **azul** y pulsa sobre él automáticamente.
Funciona encima de otras apps con un panel flotante, usando únicamente las API oficiales de Android.

> Objetivo de rendimiento: intentar responder en **< 200 ms**. No se garantiza ninguna velocidad concreta: depende del dispositivo,
> de la tasa de refresco del juego y de lo que tarde Android en inyectar el gesto. La app mide y muestra el retraso configurado
> **por separado** de la latencia real del sistema.

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

1. Abre **Instant React** y concede:
   * **Ventana flotante** (Mostrar sobre otras apps).
   * **Accesibilidad**: Ajustes → Accesibilidad → Instant React → activar. Se usa *solo* para ejecutar los toques; no lee tu pantalla.
   * **Captura de pantalla**: pulsa *Activar captura y panel flotante* y acepta. En Android 14+ elige **«Toda la pantalla»** (no «una sola app»).
2. Abre tu juego y pulsa **◎ Calibrar** en el panel flotante (o *Calibrar* en la app):
   * Pulsa **Auto** para que localice los 6 círculos grises, o arrastra los 6 marcadores a mano.
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

* **Unitarias (JVM, 38 pruebas)**: detección de los 6 círculos en 7 resoluciones, gris→azul en cada círculo, cambios rápidos, ausencia de pulsaciones duplicadas, precisión de coordenadas, escalado entre resoluciones, brillo, *stride* de filas, latencia de procesado, estadísticas, autodetección de círculos y textos del panel.
* **Instrumentadas (emulador Android 34 en CI)**: arranque de la app sin cierres, ventana flotante, fotogramas reales por `ImageReader`, toque real por accesibilidad con coordenadas exactas y cadena completa fotograma → detección → toque en pantalla.
* El diálogo de permiso de `MediaProjection` exige una persona, por lo que la captura real de pantalla hay que probarla a mano en tu móvil.

El workflow `.github/workflows/android.yml` compila, ejecuta las pruebas, genera los APK y los sube como *artifacts*.

## Limitaciones

* Android obliga a aceptar el diálogo de captura cada vez que se inicia el servicio.
* Apps con `FLAG_SECURE` (algunas apps bancarias/DRM) no se pueden capturar.
* `dispatchGesture` añade una latencia propia del sistema que la app no puede eliminar; por eso se muestra separada.
* Los juegos en línea pueden prohibir las pulsaciones automáticas en sus condiciones de uso: úsala solo donde esté permitido.
