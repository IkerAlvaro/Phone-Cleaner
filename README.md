# Phone Cleaner

App Android para **limpiar y optimizar el móvil**: archivos basura, caché de las apps, RAM, batería, CPU y fotos o vídeos que ocupan demasiado. Está inspirada en el funcionamiento de CCleaner para Android, pero tiene identidad propia.

- **Interfaz:** blanco y verde en modo claro, negro puro (AMOLED) y verde en modo oscuro.
- **Idiomas:** español, e inglés si el móvil está en inglés.
- **Privacidad:** 100 % sin conexión, sin anuncios ni rastreo. La app ni siquiera pide el permiso de Internet.

| Inicio | Limpieza | Sistema | Archivos |
|---|---|---|---|
| ![](docs/screenshots/01_inicio_claro.png) | ![](docs/screenshots/02_limpieza_claro.png) | ![](docs/screenshots/08_sistema_claro.png) | ![](docs/screenshots/09_archivos_claro.png) |
| ![](docs/screenshots/01_inicio_oscuro.png) | ![](docs/screenshots/02_limpieza_oscuro.png) | ![](docs/screenshots/08_sistema_oscuro.png) | ![](docs/screenshots/09_archivos_oscuro.png) |

Todas las capturas, en claro y oscuro, están en [`docs/screenshots/`](docs/screenshots).

## Funciones

| Función | Qué hace |
|---|---|
| **Análisis rápido / Limpieza** | Busca miniaturas, temporales y registros, carpetas vacías, APK sobrantes, descargas antiguas y restos de apps desinstaladas. Las categorías dudosas vienen desmarcadas. En Android 11 o superior, **vacía la caché de todas las apps con un toque** usando la acción oficial del sistema. |
| **Liberar RAM** | Un toque y Phone Cleaner **llena la memoria unos segundos** para que el propio sistema de Android (el *low memory killer*) cierre las apps en segundo plano; Phone Cleaner no se cierra. Después suelta la memoria y muestra lo liberado de verdad, medido antes y después. Funciona en cualquier versión de Android, sin Accesibilidad, sin root y sin apps extra (detalles abajo). |
| **Gestor de apps** | Ordena por tamaño, nombre, instaladas hace poco o «se inician solas». Cada app tiene una ficha con su tamaño, fechas de instalación y actualización, si arranca sola al encender el móvil y su estado de batería. Se pueden detener o desinstalar varias a la vez. |
| **Batería** | Muestra nivel, salud, temperatura, voltaje, corriente, capacidad estimada y ciclos. También las **apps que se inician solas al encender el móvil**, las apps sin restricción de batería y accesos directos para ahorrar. |
| **Sistema (monitor)** | Muestra la CPU (frecuencia por núcleo y actividad estimada), la temperatura y el estado térmico, la RAM, el almacenamiento y la batería en tiempo real. Si el móvil se calienta, da consejos. |
| **Fotos y archivos** | Encuentra fotos **duplicadas** (copias exactas, comparadas por MD5), fotos **similares** (ráfagas, con hash perceptual), capturas de pantalla, vídeos grandes y archivos grandes. Lo que borras va a la papelera del sistema y se puede recuperar durante 30 días. |
| **Comprimir** | Recomprime **fotos** (JPEG al 85 %, misma resolución, conservando fecha, GPS y orientación) y **vídeos** (HEVC con Media3, con el bitrate que mantiene la calidad). Solo guarda el resultado si ahorra de verdad. Después ofrece mover los originales a la papelera. |
| **Ajustes** | Tema (según el sistema, claro u oscuro), recordatorio semanal de limpieza y estado de los permisos. |

### Lo que Android no permite (y cómo lo resuelve la app)

- Desde Android 14, una app normal **no puede cerrar otras apps** directamente.
- Ninguna app normal puede cambiar la velocidad de la CPU.
- Ninguna app normal puede leer el consumo exacto de batería de cada app.

Además, Android 15 marca como **«ajuste restringido»** varios permisos cuando la app se instala desde un APK descargado, fuera de una tienda: el acceso de uso, la Accesibilidad y mostrar sobre otras apps, entre otros. Esos permisos solo se pueden activar con un paso extra de seguridad. Por eso Phone Cleaner **no pide el acceso de uso ni la Accesibilidad**:

- **Liberar RAM por presión de memoria.** La app reserva memoria fuera del heap de Java (`SharedMemory`, o los píxeles de un `Bitmap` en Android 8.0) y la rellena con datos que no se pueden comprimir. Si no, la compresión zram del móvil la anularía. Ante esa presión, el *low memory killer* de Android cierra primero las apps en caché y en segundo plano. La app en pantalla es la última, así que Phone Cleaner sigue abierto.
- **Freno de seguridad.** La reserva se para antes del umbral de «memoria baja» de Android (con un margen), al llegar al 75 % de la RAM, a los 10 segundos o en cuanto el sistema avisa (`onTrimMemory`). Después se suelta toda la memoria, también si sales de la pantalla a mitad.
- **Contrapartida.** Las apps cerradas tardan algo más en abrirse la próxima vez, porque Android tiene que volver a cargarlas.
- **Datos de las apps sin permisos especiales.** El tamaño de cada app sale de sus APK y la lista de «apps que se inician solas» sale de los permisos que declaran. El tamaño de los datos y la caché de cada app solo se ve en sus Ajustes.
- **Caché de todas las apps.** Se vacía con la acción oficial del sistema (Android 11 o superior). Lo liberado se mide con el espacio libre antes y después.
- **CPU.** La carga se llama «actividad estimada», porque se calcula a partir de las frecuencias.

«Sin perder calidad» significa **sin diferencia visible**. Una compresión matemáticamente sin pérdida apenas ahorra espacio.

## Permisos

| Permiso | Para qué | Si no se concede |
|---|---|---|
| Acceso a todos los archivos (`MANAGE_EXTERNAL_STORAGE`) | Buscar basura y vaciar la caché de todas las apps | Solo se analiza la caché de las apps, y se vacía app por app |
| Fotos y vídeos (`READ_MEDIA_*`, `ACCESS_MEDIA_LOCATION`) | Duplicados, compresión y conservar el GPS de las fotos | No se pueden analizar ni comprimir fotos |
| Notificaciones | Recordatorio semanal y progreso de la compresión | No hay avisos |
| `KILL_BACKGROUND_PROCESSES`, `REQUEST_DELETE_PACKAGES` | Refuerzo de «Liberar RAM» en Android 13 o inferior y desinstalar | — |

La visibilidad de otras apps se limita a las que tienen icono (`<queries>`). La app no usa `QUERY_ALL_PACKAGES`.

## Compilar e instalar

Requisitos: JDK 17 o superior y el SDK de Android con la plataforma 37.

```bash
./gradlew :app:assembleDebug          # APK de prueba: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # tests unitarios y de capturas
./gradlew :app:recordRoborazziDebug   # regenera las capturas de docs/screenshots
./gradlew :app:lintDebug
```

Para instalar el APK en tu móvil, cópialo al teléfono y ábrelo (tendrás que permitir «instalar apps desconocidas»), o usa `adb install app-debug.apk`.

Datos técnicos:

- Kotlin y Jetpack Compose con Material 3.
- minSdk 26 (Android 8), targetSdk 36 y compileSdk 37.
- R8 activado en la versión release, que pesa unos 2 MB.

## Publicar en Google Play

1. **Crea una clave de subida** una sola vez y guárdala bien, porque sin ella no podrás actualizar la app:
   ```bash
   keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **Crea `keystore.properties`** en la raíz del proyecto. Ya está en `.gitignore`, así que no se sube a git:
   ```properties
   storeFile=upload-key.jks
   storePassword=TU_CONTRASEÑA
   keyAlias=upload
   keyPassword=TU_CONTRASEÑA
   ```
3. **Genera el paquete con `./gradlew :app:bundleRelease`.** El resultado queda en `app/build/outputs/bundle/release/app-release.aab`.
4. En **Play Console**, crea la app, sube el `.aab` y rellena:
   - **Acceso a todos los archivos:** explica que es una app de limpieza y gestión de almacenamiento. Google puede rechazar este permiso. Si lo hace, quítalo del manifiesto: la app sigue funcionando sin él, aunque la limpieza queda limitada a la caché de cada app.
   - **Permisos de fotos y vídeos:** la función principal es encontrar duplicados y comprimir.
   - **Servicios en primer plano:** de tipo *procesamiento multimedia* y *sincronización de datos*, para comprimir vídeos.
   - **Seguridad de los datos:** no se recopila ni se comparte ningún dato, porque la app funciona sin conexión.
   - **Liberar RAM:** Google podría considerar que llenar la memoria para que Android cierre otras apps es «interferir con otras apps», según su política de abuso del dispositivo. Si lo rechaza, se puede dejar solo el refuerzo de Android 13 o inferior.
5. **Revisa el `applicationId`** (`com.ikeralvaro.phonecleaner`) antes de la primera subida, porque después no se puede cambiar.

## Lista de pruebas en un móvil real

- [ ] El onboarding concede cada permiso y vuelve a la app con el estado actualizado.
- [ ] El análisis rápido encuentra basura y «Limpiar» libera lo indicado.
- [ ] «Vaciar caché de todas las apps» muestra el diálogo del sistema y después la cifra liberada (Android 11 o superior).
- [ ] Liberar RAM funciona. Para comprobarlo:
  1. Abre varias apps pesadas y entra en Liberar RAM.
  2. Pulsa el botón. El anillo debe subir y bajar, y Phone Cleaner no debe cerrarse.
  3. La cifra liberada debe ser mayor que 0.
  4. Al volver a «Apps recientes», las otras apps tienen que recargarse.
- [ ] Borrar una foto duplicada la manda a la papelera.
- [ ] Al comprimir una foto, la copia conserva la fecha y la ubicación.
- [ ] Al comprimir un vídeo, se ve igual y ocupa menos.
- [ ] Cambiar entre tema claro y oscuro funciona, y el modo «según el sistema» sigue al móvil.
- [ ] Con el móvil en inglés, la app aparece en inglés.

## Estructura

```
app/src/main/java/com/ikeralvaro/phonecleaner/
  core/        permisos, lectores del sistema (RAM, almacenamiento, batería, CPU, temperatura), utilidades
  feature/     home, cleaner, boost, apps, battery, monitor, media, compress, settings, onboarding
  ui/          tema verde/blanco/negro, componentes y navegación
app/src/test/  tests de la lógica (reglas de basura, hashes de fotos, compresión…) y capturas de pantalla
docs/          especificación de diseño y capturas
```
