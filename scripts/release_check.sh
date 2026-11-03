#!/usr/bin/env bash
# Dentro del emulador: instala el APK RELEASE (R8 + recursos reducidos) y comprueba el servicio de accesibilidad.
set -u
API="${1:-?}"
mkdir -p evidence
PKG=com.instantreact
A11Y=$PKG/com.instantreact.service.ReactAccessibilityService
LOG=evidence/release_api$API.log
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
FAIL=0
fail() { say "FALLO: $*"; FAIL=1; }

say "== API $API · $(adb shell getprop ro.build.version.release) · instalando APK release"
adb install -r dist/InstantReact-release.apk 2>&1 | tee -a "$LOG"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; adb shell input keyevent 82

adb logcat -c
say "== Abrir la app y activar el servicio (como hace el usuario en Ajustes)"
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 3
adb shell settings put secure enabled_accessibility_services $A11Y
adb shell settings put secure accessibility_enabled 1
sleep 5
adb shell dumpsys accessibility > evidence/a11y_api$API.txt
grep -iE "instantreact|Bound services|Enabled services|Crashed" evidence/a11y_api$API.txt | head -12 | tee -a "$LOG"
adb logcat -d -s InstantReact:V > evidence/app_api$API.log
cat evidence/app_api$API.log | tee -a "$LOG"
grep -q "onServiceConnected OK" evidence/app_api$API.log || fail "onServiceConnected no se ejecutó"
grep -q "canPerformGestures=true" evidence/app_api$API.log || fail "canPerformGestures no concedido"
# El servicio debe aparecer en 'Bound' y no en 'Crashed'
if grep -iE "Crashed services:\{.*Instant React" evidence/a11y_api$API.txt; then fail "el servicio figura como 'crashed' (esto es lo que Android muestra como «No funciona»)"; fi
grep -iE "Bound services:\{.*Instant React" evidence/a11y_api$API.txt >/dev/null || fail "el servicio no figura en 'Bound services'"
PID1=$(adb shell pidof $PKG | tr -d '\r')

say "== Pulsación de prueba (dispatchGesture sobre TapTestActivity)"
adb shell am start -W -n $PKG/.TapTestActivity >/dev/null
sleep 5
adb exec-out screencap -p > evidence/taptest_api$API.png
adb logcat -d -s InstantReact:V | grep SELFTEST | tee -a "$LOG"
adb logcat -d -s InstantReact:V | grep -q "SELFTEST OK" || fail "la pulsación de prueba no llegó"

say "== Cambiar a otra aplicación y volver"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 4
adb shell dumpsys accessibility > evidence/a11y2_api$API.txt
grep -iE "Bound services:\{.*Instant React" evidence/a11y2_api$API.txt >/dev/null || fail "el servicio dejó de estar activo al cambiar de app"
grep -iE "Crashed services:\{.*Instant React" evidence/a11y2_api$API.txt && fail "el servicio quedó 'crashed' tras cambiar de app"
PID2=$(adb shell pidof $PKG | tr -d '\r')
say "PID antes=$PID1 después=$PID2"
[ "$PID1" = "$PID2" ] || fail "el proceso se reinició"
adb shell am start -W -n $PKG/.TapTestActivity >/dev/null; sleep 5
adb logcat -d -s InstantReact:V | grep -c "SELFTEST OK" | xargs -I{} echo "SELFTEST OK veces: {}" | tee -a "$LOG"
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime" | head -20 | tee -a "$LOG"
adb logcat -d | grep -q "FATAL EXCEPTION" && fail "excepción fatal en logcat"
adb logcat -d > evidence/logcat_api$API.txt
[ $FAIL -eq 0 ] && say "RESULTADO API $API: TODO OK" || say "RESULTADO API $API: HAY FALLOS"
exit $FAIL
