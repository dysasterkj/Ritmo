# Ritmo — notas para Claude Code

App Android (Kotlin + Jetpack Compose) de música en streaming para uso personal. Paquete: `com.kjota.ritmo`. Interfaz en español.
Catálogo = YouTube, a través de yt-dlp (youtubedl-android). Sin cuentas, sin anuncios, sin analítica.

## Estado
Escrita **sin poder compilarse** (el entorno no tiene Android SDK ni Maven). La primera tarea es compilar con
`./gradlew assembleDebug` (o con GitHub Actions) y corregir lo que falle.

## Cómo funciona
- Búsqueda / "radio": `engine/YouTube.kt` usa `ytsearch25:<texto>` y `watch?v=ID&list=RDID` con `--flat-playlist -J`.
- Reproducción: `player/PlaybackService.kt` (Media3 `MediaSessionService` + ExoPlayer). Cada canción es el URI `ritmo:<id>`;
  un `ResolvingDataSource` lo convierte en archivo local (si está descargada) o en la URL directa que da `yt-dlp -g`.
  Las URLs se cachean 3 h (`YouTube.urlCache`) y se precarga la siguiente canción.
- Descargas: `download/Downloads.kt` (cola + progreso) y `DownloadService.kt` (primer plano, una por una) → `filesDir/music/<id>.<ext>`.
- Biblioteca (favoritas, descargas, recientes, playlists): `data/Library.kt`, JSON en `filesDir/library.json`.
- UI: `ui/Glass.kt` (aurora + `Modifier.glass()` / `glassBar()`), pantallas en `ui/*Screen.kt`, `RitmoRoot.kt` (navegación y mini reproductor).
- `MainViewModel` conecta la UI con el reproductor mediante `MediaController`.

## Reglas heredadas de Bajalo (no cambiar sin motivo)
- `android:extractNativeLibs="true"` y `useLegacyPackaging = true` (necesarios para yt-dlp/Python). `minSdk = 29`.
- Versiones: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Compose BOM 2024.12.01, Coil 2.7.0, youtubedl-android 0.18.1, Media3 1.5.0.
- No agregar SDKs de publicidad ni telemetría. No implementar nada para saltarse DRM.
- Si YouTube deja de responder: actualizar yt-dlp (Ajustes → "Actualizar motor ahora"; también se actualiza solo cada 24 h).

## Ideas pendientes
Cola visible, temporizador de apagado, letras, ecualizador, importar playlists de YouTube por enlace, caché de portada de Inicio sin conexión.
