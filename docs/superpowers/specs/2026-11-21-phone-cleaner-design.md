<!-- Especificación de diseño aprobada el 2026-11-21. -->
# Plan: "Phone Cleaner", una app Android de limpieza y optimización (inspirada en CCleaner)

## Contexto

El usuario quiere una app Android que limpie y optimice el móvil (batería, CPU, RAM y almacenamiento), con un funcionamiento parecido a CCleaner para Android. La interfaz debe ser **blanca y verde** en modo claro y **negra y verde** en modo oscuro. El repositorio `ikeralvaro/phone-cleaner` está vacío: es un proyecto nuevo.

Lo que el usuario decidió:
- **Se publicará en Google Play.** Por eso no se usa el servicio de Accesibilidad, porque Play lo restringe. Todo se hace con API públicas, y las cifras que se muestran son reales: nada de animaciones de "impulso" falsas, que además Play rechaza como engañosas.
- **Funciones de la v1:**
  - Limpieza rápida
  - Gestor de apps
  - Monitor del sistema
  - Fotos y archivos
  - Liberar RAM
  - Compresión de fotos y vídeos "sin perder calidad"
- **Idiomas:** español por defecto, inglés según el idioma del móvil.

Mis decisiones, que el usuario puede corregir:
- Nombre: "Phone Cleaner", como el repositorio.
- Paquete: `com.ikeralvaro.phonecleaner`.
- Icono propio: escoba o hoja blanca sobre verde, sin nada de la marca CCleaner.
- **Sin permiso de INTERNET:** la app funciona 100 % sin conexión y no tiene anuncios ni rastreo. Esto simplifica el formulario de seguridad de datos de Play.
- **"Sin perder calidad"** significa *sin pérdida visible*. No es compresión matemáticamente sin pérdida, porque esa apenas reduce el tamaño. La interfaz lo dirá con esas palabras.

## Entorno verificado

- Java 21, Gradle 8.14.3 local y SDK de Android en `/opt/android-sdk` (platform 35 y build-tools 35).
- `dl.google.com`, Maven Central y services.gradle.org son accesibles.
- **No hay KVM**, así que no se puede usar el emulador. La verificación se hará con tests en la JVM (Robolectric y Roborazzi), lint, compilación del APK y capturas generadas.
- Instalaré `platforms;android-36` con `sdkmanager`, porque Play exige targetSdk 36 en 2026.

## Stack

- Kotlin y Jetpack Compose con Material 3. Una sola Activity, con Navigation Compose.
- MVVM: un ViewModel con `StateFlow` por pantalla.
- Inyección de dependencias manual con un `AppContainer` en la clase `Application`, sin Hilt, para que la compilación sea más simple y rápida.
- Librerías:
  - DataStore para los ajustes;
  - WorkManager para los recordatorios y las compresiones largas;
  - Coil 3 para las miniaturas;
  - Media3 Transformer para el vídeo;
  - ExifInterface para conservar los metadatos de las fotos.
- Gradle wrapper 9.x y AGP 9.5.x, con el Kotlin integrado (2.4.x) y el plugin del compilador de Compose.
- Las versiones van en `gradle/libs.versions.toml`, fijadas a versiones estables con al menos dos semanas de antigüedad: Media3 1.11.1, Lifecycle 2.11.x, Activity 1.13.x, y un Compose BOM estable anterior a 2026.11.01.
- minSdk 26 (Android 8), compileSdk y targetSdk 36. R8 activado en la versión release.

## Estructura (un solo módulo `:app`, paquetes por función)

```
app/src/main/java/com/ikeralvaro/phonecleaner/
  PhoneCleanerApp.kt, AppContainer.kt, MainActivity.kt
  ui/theme/        Color.kt, Theme.kt, Type.kt   (paleta verde/blanco/negro)
  ui/components/   UsageRing (medidor circular), StatCard, CategoryRow (con casilla y tamaño),
                   PermissionCard, BigActionButton, SectionHeader, EmptyState, ProgressSheet
  ui/navigation/   AppNavHost, BottomBar (Inicio · Apps · Archivos · Sistema), Routes
  core/permissions PermissionManager (estado y Intents de cada permiso especial)
  core/system/     MemoryReader, StorageReader, BatteryReader, CpuReader, ThermalReader
  core/util/       Bytes.kt (formatBytes), Time.kt
  feature/home/    HomeScreen/VM
  feature/cleaner/ JunkRules (lógica pura), JunkScanner, AppCacheReader, CleanerScreen/VM, CleanResultScreen
  feature/boost/   RamBooster, QuickStopFlow, BoostScreen/VM
  feature/apps/    AppRepository, AppsScreen/VM, AppDetailScreen
  feature/battery/ BatteryScreen/VM (estado, apps más activas, consejos)
  feature/monitor/ MonitorScreen/VM (RAM, CPU, batería y temperatura en directo)
  feature/media/   MediaRepository, DuplicateFinder, PerceptualHash (dHash), MediaScreen/VM, MediaReviewScreen
  feature/compress/PhotoCompressor, VideoCompressor, CompressionPlanner (lógica pura), CompressWorker, CompressScreen/VM
  feature/settings/SettingsRepository (DataStore), SettingsScreen, CleanReminderWorker
  feature/onboarding/OnboardingScreen (bienvenida y permisos)
```

## Interfaz (al estilo de CCleaner, con identidad propia)

- **Tema:**
  - Claro: fondo `#FFFFFF`, tarjetas `#F1F8F3`, verde primario `#16A34A`, contenedor primario `#DCFCE7`, texto `#0F1A14`.
  - Oscuro: fondo negro puro `#000000` (ideal para AMOLED), tarjetas `#0D1410`, verde primario `#22C55E`, contenedor primario `#14532D`, texto `#E8F5EC`.
  - El color dinámico de Material You está desactivado para mantener el verde de la marca.
  - Ajuste de tema con tres opciones: Sistema, Claro u Oscuro. La app ocupa toda la pantalla (edge-to-edge) y tiene pantalla de inicio (splash) verde.
- **Inicio:**
  - medidor circular del almacenamiento ("87 GB de 128 GB") y barra de RAM;
  - botón grande verde **"Análisis rápido"**;
  - cuadrícula de accesos: Limpieza, Liberar RAM, Batería, Apps, Fotos y archivos, Comprimir y Monitor;
  - última limpieza y espacio liberado en total.
- **Flujo de limpieza:**
  1. Animación de análisis con el progreso por categoría.
  2. Resultados agrupados, con casillas y tamaños. Las categorías seguras vienen marcadas y las que conviene revisar, desmarcadas.
  3. Botón "Limpiar 1,2 GB".
  4. Pantalla de resultado con lo liberado de verdad, medido antes y después.

## Funciones y API concretas

**Permisos.** El onboarding los explica y los pide uno a uno. Cada función degrada con elegancia si falta su permiso:
- Acceso a todos los archivos (`MANAGE_EXTERNAL_STORAGE`, que se abre con `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`). Sirve para analizar la basura y borrar la caché de todas las apps.
- Acceso de uso (`PACKAGE_USAGE_STATS`, con `ACTION_USAGE_ACCESS_SETTINGS`). Sirve para los tamaños y la caché de cada app y para su uso y última apertura.
- Notificaciones (API 33 o superior).
- `READ/WRITE_EXTERNAL_STORAGE` con `maxSdkVersion=29` y `requestLegacyExternalStorage` para Android 10.
- `KILL_BACKGROUND_PROCESSES` y `REQUEST_DELETE_PACKAGES`.
- `<queries>` para la intent MAIN/LAUNCHER, en lugar de `QUERY_ALL_PACKAGES`, que Play restringe.

**1. Limpieza rápida** (`JunkRules` es una función pura que clasifica cada ruta en una categoría; `JunkScanner` recorre el almacenamiento externo y se salta `Android/data` y `Android/obb`):
- Marcadas por defecto:
  - APKs de instalación, mostrando si la app ya está instalada (`getPackageArchiveInfo`);
  - miniaturas (`.thumbnails`);
  - archivos temporales y logs (`*.tmp`, `*.log`, …);
  - carpetas vacías;
  - restos de apps desinstaladas (`Android/media/<paquete>` cuyo paquete ya no existe).
- Desmarcadas, para revisar: descargas de más de 90 días.
- **Caché de todas las apps:** el total sale de `StorageStatsManager.queryStatsForPackage`.
  - En Android 11 o superior, un toque lanza `StorageManager.ACTION_CLEAR_APP_CACHE`. Es la vía oficial: el sistema pide confirmación y vacía la caché de todas las apps.
  - En Android 8 a 10 no hay API para eso: la app lista las apps por tamaño de caché y abre los Ajustes de cada una.
- La app vacía siempre su propia caché.
- Borrado: con acceso a todos los archivos, directamente; si no, con `MediaStore.createDeleteRequest`.

**2. Liberar RAM** (`RamBooster`):
- Las candidatas son las apps usadas en las últimas 3 horas, según `UsageStatsManager`, quitando la propia app, el launcher y el teclado.
- En Android 13 o inferior se llama a `ActivityManager.killBackgroundProcesses` para cada una.
- En Android 14 o superior esa API solo puede cerrar la propia app. Por eso se usa la **"Detención rápida" guiada** (`QuickStopFlow`): abre los Ajustes de cada app seleccionada, el usuario pulsa "Forzar detención" y, al volver, la app pasa a la siguiente.
- La RAM libre se mide con `ActivityManager.MemoryInfo` antes y 2 s después, y se muestra la diferencia real.

**3. Gestor de apps:**
- Lista ordenable por tamaño total, caché, tiempo de uso, "sin usar en 30 días" o última apertura.
- La ficha de cada app muestra el desglose (app, datos y caché), si tiene la optimización de batería desactivada (`PowerManager.isIgnoringBatteryOptimizations`) y estos botones:
  - Abrir ajustes (para forzar la detención o borrar la caché);
  - Desinstalar (`ACTION_DELETE`);
  - Detención rápida.
- Desinstalación en lote, una confirmación tras otra.

**4. Batería:**
- Datos de `ACTION_BATTERY_CHANGED`: nivel, estado, salud, temperatura y voltaje. Con `BatteryManager` se añaden la corriente actual, la capacidad estimada (`CHARGE_COUNTER` entre el nivel) y los ciclos (`EXTRA_CYCLE_COUNT`, en API 34 o superior).
- Apps con más tiempo en pantalla en las últimas 24 h. La etiqueta dice "tiempo de uso", no "consumo", porque Android no da el consumo por app a terceros.
- Apps sin restricción de batería, con enlace a sus ajustes.
- Accesos directos: ahorro de batería (`ACTION_BATTERY_SAVER_SETTINGS`), pantalla y brillo, y modo oscuro.

**5. Monitor del sistema** (se refresca cada 1 s mientras está visible, con `repeatOnLifecycle`):
- RAM: usada, libre y umbral bajo.
- Almacenamiento (`StatFs`).
- CPU:
  - número de núcleos;
  - frecuencia actual y máxima de cada núcleo, leída de `/sys/devices/system/cpu/cpuN/cpufreq` (si no se puede leer, se muestra "no disponible");
  - "actividad estimada", que es la media de la frecuencia actual entre la máxima. El uso real en % no se puede leer desde Android 8.
- Estado térmico (`PowerManager.currentThermalStatus` y `getThermalHeadroom`).
- Temperatura de la CPU desde `thermal_zone*` si se puede leer; si no, la de la batería.
- Si el móvil se calienta, la app recomienda liberar RAM y activar el ahorro de batería.

**6. Fotos y archivos** (`MediaRepository` sobre MediaStore):
- **Duplicados exactos:** se agrupan por tamaño y después por hash MD5 del contenido.
- **Fotos similares:** dHash de 64 bits sobre una miniatura en escala de grises de 9×8 y distancia de Hamming ≤ 6.
- En cada grupo se propone conservar la de mayor resolución y, si empatan, la más reciente.
- Otras categorías: capturas de pantalla, vídeos grandes, archivos grandes (más de 100 MB).
- Pantalla de revisión en cuadrícula con selección. El borrado manda los archivos a la papelera del sistema con `createTrashRequest` (en API 30 o superior; se recuperan durante 30 días).

**7. Compresión de fotos y vídeos** (`CompressionPlanner` es pura y testeable; el trabajo se hace en `CompressWorker`, con WorkManager en primer plano y una notificación de progreso):
- **Fotos:**
  - se decodifican con `ImageDecoder` (o `BitmapFactory` en API 26-27);
  - se recodifican en JPEG con calidad 85, en la opción "Alta calidad · sin diferencia visible", que es la predeterminada. Hay otra opción "Máximo ahorro";
  - se conservan EXIF (fecha, GPS, orientación) y `DATE_TAKEN`, para no alterar el orden de la galería;
  - solo se guarda el resultado si ahorra al menos un 10 %.
- **Vídeos:**
  - se transcodifican con Media3 `Transformer` a H.265/HEVC si el codificador del móvil lo soporta; si no, a H.264;
  - el bitrate objetivo es el que mantiene la calidad para su resolución (unos 5 Mbps en 1080p30 y 16 Mbps en 4K con HEVC); si eso no ahorra al menos un 15 %, el vídeo no se toca;
  - el audio se copia tal cual;
  - el resultado se descarta si no ahorra al menos un 15 %.
- Antes de comprimir se muestra el ahorro estimado. El original va a la papelera del sistema (en Android 11 o superior); en Android 8 a 10 se conservan las dos copias, salvo que el usuario elija lo contrario.

**8. Ajustes y onboarding:**
- Ajustes: tema, recordatorio semanal de limpieza (un `PeriodicWorkRequest` hace un análisis ligero y manda la notificación "Tienes X MB de basura"), estado de los permisos y "Acerca de".
- Idiomas en `values/strings.xml` (español) y `values-en/strings.xml` (inglés). `locales_config.xml` permite elegir el idioma por app en Android 13 o superior.
- Icono adaptativo propio.

## Orden de implementación

1. Guardar la especificación de diseño en `docs/superpowers/specs/2026-11-21-phone-cleaner-design.md`. Preparar el esqueleto: wrapper de Gradle, catálogo de versiones, `:app`, tema, navegación con barra inferior y pantallas vacías. Instalar `android-36` y comprobar que `assembleDebug` compila.
2. Núcleo: `PermissionManager`, los lectores de `core/system`, `formatBytes` y los tests unitarios.
3. Inicio y Monitor.
4. Limpieza rápida, con `JunkRules` desarrollado con tests primero.
5. Liberar RAM, Gestor de apps y Batería.
6. Fotos y archivos (`PerceptualHash` y `DuplicateFinder` con tests primero).
7. Compresión de fotos y vídeos (`CompressionPlanner` con tests primero).
8. Ajustes, onboarding, recordatorio, traducciones al inglés e icono.
9. Tests de capturas, lint, APK, README (incluida la guía para publicar en Play), commit y push a `ccr-00bb79d9-979ebl`.

## Verificación

- `./gradlew :app:testDebugUnitTest`, con tests de:
  - `JunkRules`, `PerceptualHash` y la agrupación de duplicados;
  - `CompressionPlanner` (calidad y bitrate según la resolución y el umbral de ahorro);
  - `formatBytes`, la salud de la batería y la estimación de actividad de la CPU.
- **Capturas con Roborazzi y Robolectric** de Inicio, Limpieza (resultados), Apps, Archivos, Monitor y Ajustes, en **modo claro y oscuro** y con datos falsos. Se las envío al usuario para que revise la interfaz sin necesidad de un móvil.
- `./gradlew :app:lintDebug` sin errores. `./gradlew :app:assembleDebug assembleRelease` compila, con R8 activado en release.
- `apkanalyzer manifest print` para confirmar que el manifiesto no incluye INTERNET, Accesibilidad ni `QUERY_ALL_PACKAGES`.
- Entrega del APK de depuración con SendUserFile, junto con una **lista de pruebas en un móvil real**:
  - conceder cada permiso;
  - análisis y limpieza;
  - borrar la caché de todas las apps;
  - detención rápida;
  - borrar un duplicado;
  - comprimir una foto y un vídeo;
  - cambiar el tema.

## Riesgos y notas para Play

- Al publicar, `MANAGE_EXTERNAL_STORAGE` exige rellenar una declaración en Play Console, y Google puede rechazarla. Si eso ocurre, la app sigue funcionando sin ese permiso: el análisis de basura queda limitado y la caché se borra app por app. Las funciones de fotos, compresión, apps, RAM, batería y monitor no dependen de él.
- Publicar requiere un AAB firmado con una clave de subida que el usuario genere. El README lo explica (`bundleRelease`).
- Sin acceso de uso no se pueden mostrar los tamaños, la caché ni el uso de otras apps. La app explica por qué lo necesita.


## Cambio 2026-11-21 (tras la primera prueba en un móvil real)

El «Acceso de uso» se eliminó.

**Motivo.** Desde Android 15, es un *ajuste restringido* para las apps instaladas desde un APK: el sistema lo bloquea «por seguridad» y hay que desbloquearlo a mano.

**Qué lo sustituye:**
- **Gestor de apps:** tamaño de los APK, fechas de instalación y actualización, y «se inicia sola» (la app declara `RECEIVE_BOOT_COMPLETED`).
- **Batería:** la lista de «apps que se inician solas» en lugar del tiempo de uso.
- **Caché de todas las apps:** se mide con el espacio libre antes y después.

**Liberar RAM.** A petición del usuario, ahora funciona por **presión de memoria**:
- la app ocupa memoria nativa rellena de ruido, para que zram no la comprima;
- el *low memory killer* de Android cierra las apps en segundo plano;
- después se libera todo y se mide la diferencia real.

El freno de seguridad está en `PressurePolicy`. El margen sobre el umbral de memoria baja es un cuarto del umbral, con un mínimo de 96 MB. Quedó más ajustado que el 1,5× previsto, porque con ese margen se pararía antes de que Android empezara a cerrar las apps en caché. La detención rápida guiada se eliminó de esta pantalla. En el gestor de apps se puede seguir deteniendo cada app desde su ficha.


## Cambio: monetización (2026-11-21)

El usuario pidió anuncios «no tan intrusivos, pero que se vean» y eligió estos formatos:
- **Nativos en tarjetas:** en Inicio y en las pantallas de resultado de limpieza, RAM y compresión. Tienen el estilo de la app y la etiqueta «Anuncio».
- **Pantalla completa al salir de un resultado:** con `AdFrequencyPolicy`: nunca en la primera tarea, como mínimo 3 minutos entre anuncios y como mucho 6 al día.
- **Compra única «Quitar anuncios»:** con Play Billing 9 (`remove_ads`).

Detalles técnicos:
- **Consentimiento:** con UMP, obligatorio en el EEE. La opción «Privacidad de los anuncios» está en Ajustes.
- **IDs de AdMob:** la versión de depuración usa siempre los de prueba; la release los lee de `admob.properties`.
- **Privacidad:** la app deja de ser «100 % sin conexión». Los archivos siguen sin salir del móvil, pero el SDK de AdMob usa Internet y el ID de publicidad. Se actualizaron los textos y el README, y se añadió `PRIVACY.md`.
