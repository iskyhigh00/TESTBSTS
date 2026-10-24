# Se achica el código pero sin renombrar: el Registro y los errores siguen mostrando nombres legibles.
-dontobfuscate

# usb-serial-for-android busca los drivers por reflexión (getSupportedDevices): no tocar.
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**
