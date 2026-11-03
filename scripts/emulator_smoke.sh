#!/usr/bin/env bash
# Se ejecuta DENTRO del emulador (Android 34) desde el workflow de CI.
set -u
mkdir -p evidence
PKG=com.instantreact.debug
LOG=evidence/smoke.log
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }

say "== Instalando APK debug y APK de pruebas"
adb install -r dist/InstantReact-debug.apk 2>&1 | tee -a "$LOG"
adb install -r dist/InstantReact-androidTest.apk 2>&1 | tee -a "$LOG"
adb shell pm list packages | grep instantreact | tee -a "$LOG"

say "== Concediendo permisos (ventana flotante) y activando accesibilidad"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
A11Y=$PKG/com.instantreact.service.ReactAccessibilityService
# Nota: 'am instrument' hace force-stop de la app y Android desactiva entonces sus servicios de accesibilidad;
# por eso se activa justo DESPUÉS de que arranque el proceso de pruebas (más abajo).

say "== Abriendo la app"
adb logcat -c
adb shell am start -W -n $PKG/com.instantreact.MainActivity | tee -a "$LOG"
sleep 3
adb exec-out screencap -p > evidence/main_screen.png
if adb logcat -d | grep -E "FATAL EXCEPTION" > evidence/fatal.txt; then
  say "!! La app se ha cerrado con una excepción"
  cat evidence/fatal.txt | tee -a "$LOG"
  adb logcat -d > evidence/logcat.txt
  exit 1
fi
say "La app arranca sin cierres"

say "== Pruebas instrumentadas"
adb shell input keyevent KEYCODE_HOME
(
  sleep 8
  adb shell settings put secure enabled_accessibility_services $A11Y
  adb shell settings put secure accessibility_enabled 1
  sleep 2
  adb shell dumpsys accessibility | grep -iE "instantreact|Bound services|Enabled services|crashed" | head -20 > evidence/a11y_dump.txt
) &
adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner > evidence/instrument.txt 2>&1
wait
say "== dumpsys accessibility"
cat evidence/a11y_dump.txt 2>/dev/null | tee -a "$LOG"
say "== logcat (accesibilidad)"
adb logcat -d | grep -iE "ReactAccessibility|AccessibilityManager.*instantreact|UiAutomation" | head -20 | tee -a "$LOG"
cat evidence/instrument.txt | grep -E "IR_METRIC|test=|INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run|stack=" | tee -a "$LOG"
adb logcat -d > evidence/logcat.txt
adb exec-out screencap -p > evidence/after_tests.png

if grep -q "INSTRUMENTATION_CODE: -1" evidence/instrument.txt && ! grep -qE "INSTRUMENTATION_STATUS_CODE: -[12]" evidence/instrument.txt; then
  say "== TODAS LAS PRUEBAS INSTRUMENTADAS PASARON"
  exit 0
fi
say "== HAY PRUEBAS INSTRUMENTADAS FALLIDAS"
exit 1
