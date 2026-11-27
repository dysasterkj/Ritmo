# Ritmo — notas para Claude Code

App Android (Kotlin + Jetpack Compose) de música en streaming para uso personal. Paquete: `com.kjota.ritmo`. Interfaz en español.
Catálogo = YouTube, a través de yt-dlp (youtubedl-android). Sin cuentas, sin anuncios, sin analítica.

## Estado
El entorno de Claude no tiene Android SDK ni Maven: el código se compila con GitHub Actions (push → workflow "Compilar APK" → Release).
Cada cambio se prueba así; el usuario instala el APK y reporta con capturas.

## Cómo funciona
- Búsqueda / "radio": `engine/YouTube.kt` usa `ytsearch25:<texto>` y `watch?v=ID&list=RDID` con `--flat-playlist -J`.
- Reproducción: `player/PlaybackService.kt` (Media3 `MediaSessionService` + ExoPlayer). Cada canción es el URI `ritmo:<id>`;
  un `ResolvingDataSource` lo convierte en archivo local (si está descargada) o en la URL directa que da `yt-dlp -g`.
  Las URLs se cachean 3 h (`YouTube.urlCache`) y se precarga la siguiente canción.
- Descargas: `download/Downloads.kt` (cola + progreso) y `DownloadService.kt` (primer plano, una por una) → `filesDir/music/<id>.<ext>`.
- Biblioteca (favoritas, descargas, recientes, playlists): `data/Library.kt`, JSON en `filesDir/library.json`.
- UI: `ui/Glass.kt` (aurora + `Modifier.glass()` / `glassBar()`), pantallas en `ui/*Screen.kt`, `RitmoRoot.kt` (navegación y mini reproductor).
- Letras: `engine/Lyrics.kt` consulta LRCLIB (`/api/search`), limpia el título de YouTube, elige por duración (±12 s) y prefiere letra con tiempos (LRC).
  Caché en `cacheDir/lyrics`. Panel en `PlayerScreen.kt` (`LyricsPanel`): resalta la línea actual y tocar una línea salta a ese momento.
- Mix DJ (v2, pre-roll): en `PlaybackService` hay DOS ExoPlayer (misma sesión de audio). ~10 s antes de la mezcla la "cola" (`tail`) carga la MISMA canción y la sigue en silencio, sincronizada (`ensurePreroll`); al empezar (`startMix`) sube a volumen 1 y el principal salta a la siguiente en silencio. Curvas: `mix_curve` 0 suave (seno/coseno), 1 corte rápido, 2 lineal. Estado visible en `player/MixBus.kt`. Descripción anterior:
  DOS ExoPlayer. Con "mix_on" (Prefs) activo y quedando ≤ `mix_sec` s, el principal salta a la siguiente
  canción (volumen sube, curva seno) y el segundo (`tail`) sigue el final de la anterior (baja, coseno). `ticker` cada 80 ms durante la mezcla.
  Un salto manual del usuario durante la mezcla la cancela (`expectTransition`).
- Importar playlists: `engine/Importer.kt`. YouTube/YT Music directo; Spotify vía página embed (`__NEXT_DATA__` → `trackList`, máx. ~100);
  Deezer vía api.deezer.com; SoundCloud/Bandcamp/otros vía yt-dlp `--flat-playlist`; texto "Artista - Canción". Lo que no es YouTube se busca
  en YouTube (3 en paralelo, se elige la duración más parecida). También entra por "Compartir" (intent SEND en el manifest).
- `MainViewModel` conecta la UI con el reproductor mediante `MediaController`.

- Ecualizador: `player/AudioFx.kt` (Equalizer + BassBoost + LoudnessEnhancer sobre la sesión de audio compartida; valores en Prefs `eq_on`, `eq_b<i>`, `bass`, `loud`). Pantalla `ui/EqualizerScreen.kt`.
- Calidad: yt-dlp `-f bestaudio/best` (Opus ~160 kbps si existe) para streaming y descargas.
- Inicio: `PullToRefreshBox`; cada refresh arma estantes distintas (pools en `MainViewModel`, chips de ánimo, "Porque escuchaste…").
- Reproductor: el host en `RitmoRoot` arrastra el reproductor verticalmente (gesto + nestedScroll) para minimizarlo; NavBar = vidrio líquido con "gota" (lente con resorte, onda al tocar).

## Reglas heredadas de Bajalo (no cambiar sin motivo)
- `android:extractNativeLibs="true"` y `useLegacyPackaging = true` (necesarios para yt-dlp/Python). `minSdk = 29`.
- Versiones: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Compose BOM 2024.12.01, Coil 2.7.0, youtubedl-android 0.18.1, Media3 1.5.0.
- No agregar SDKs de publicidad ni telemetría. No implementar nada para saltarse DRM.
- Si YouTube deja de responder: actualizar yt-dlp (Ajustes → "Actualizar motor ahora"; también se actualiza solo cada 24 h).

## Video y TV
- Reproductor con selector superior de 3 modos (carátula / letra / video, como YouTube Music) en `ui/PlayerScreen.kt` (`ModeToggle`). Los pills de abajo son solo Mix DJ, Ecualizador, Descargar y Playlist.
- `ui/VideoScreen.kt`: `rememberVideoState(vm)` crea un ExoPlayer de video SOLO mientras el modo Video (o su pantalla completa) está activo; `VideoInline` (16:9 dentro del reproductor) y `VideoFullScreen` comparten el mismo `VideoState`. El audio se pausa y se retoma con `vm.endVideo`.
- TV: manifest leanback, `tvClickable` (borde de foco), columna máx. 640dp. Pendiente: layout propio para TV y miniaturas 16:9 en Inicio/Búsqueda.

## Actualizaciones
`update/Updater.kt` consulta `releases/latest` de `dysasterkj/Ritmo` (el repo debe ser público), compara el último número del tag `v1.0.N` con `versionCode`, descarga a `cacheDir/updates` e instala con FileProvider (`REQUEST_INSTALL_PACKAGES`). Ajustes → Actualizaciones, y aviso en Inicio.

## Modo auto
`ui/CarScreen.kt`: pref `car_mode` (Ajustes o icono en Inicio); MainActivity muestra CarScreen en lugar de RitmoRoot. Horizontal forzado, botones grandes, se adapta a media pantalla (split con Waze). Botón Waze usa FLAG_ACTIVITY_LAUNCH_ADJACENT (solo sirve si ya está en pantalla dividida). TV: `ui/TvScreens.kt` (TvRoot) cuando `isTv()`.

## Widgets
`widget/Widgets.kt`: 3 tamaños (Small 2x1, Medium 4x2, Large 4x4) con RemoteViews; `WidgetUpdater` se alimenta desde PlaybackService (onEvents) y `WidgetReceiver` controla el reproductor con MediaController (play/pausa, siguiente, anterior, favorita, Favoritas, Recientes).

## Ideas pendientes
Cola visible, temporizador de apagado, caché de portada de Inicio sin conexión, importar playlists privadas de Spotify (requeriría login), beat-matching real en el Mix DJ.

## Gustos, cola y efectos (v1.0.x reciente)
- `data/Taste.kt`: 👎 bloquea canción + artista; palabras bloqueadas editables (Ajustes → Filtro de contenido); palabras extra si la semilla es de "despertar". Filtra el radio (`maybeExtendWithRadio`) y las estantes de Inicio. No filtra las búsquedas.
- Cola: `MainViewModel.queue` (QueueState) + `ui/QueueSheet.kt` (ModalBottomSheet "A continuación", se abre arrastrando el asa inferior del reproductor). El radio se encadena desde la ÚLTIMA canción para mantener ≥15 por delante, máx. 2 por artista, sin títulos repetidos.
- `AudioFx`: una cadena de efectos (EQ/Bass/Loudness) por ReproductorExo (cada uno su sesión de audio); antes compartían sesión y el limitador bombeaba durante el Mix DJ.

## Novedades (artista, temporizador, caché, BPM)
- `ui/ArtistScreen.kt`: página de artista (Dialog a pantalla completa): populares, álbumes (`YouTube.artistAlbums`/`playlistTracks`), reproducir/mezclar/no recomendar.
- `player/SleepTimer.kt`: temporizador de apagado (minutos o al terminar la canción, con fade). Chip "Temporizador" en el reproductor.
- Inicio sin conexión: `filesDir/home_cache.json` (`shelvesFromCache` en MainViewModel, declarado ANTES de `init`).
- Playlists: renombrar y reordenar (`Library.renamePlaylist`, `movePlaylistTrack`) en `CollectionScreen`.
- Mix DJ: `engine/Bpm.kt` (BPM vía Deezer) + `mixRatio` en PlaybackService (±10 % con setPlaybackSpeed); pref `mix_beat`. Es aproximado, no alinea el compás.
- Widget "Buscar": extra `open_search` → `vm.openSearch` → RitmoRoot abre la pestaña Búsqueda.
- Letras: LRCLIB `/api/get` → `/api/search` → respaldo lyrics.ovh (sin tiempos).

## Instalar en el TV
Enlace corto (apunta a la última versión): https://is.gd/ritmo2 → https://github.com/dysasterkj/Ritmo/releases/latest/download/Ritmo.apk (se usa en la app Downloader del TV).

## TV y perfiles (nov 2026)
- TV: sin Mix DJ ni ecualizador (UI oculta y mixEnabled=false en TV). Al salir de la app (onStop) se pausa el audio; foco azul (#2979FF). Al dar play (`vm.userPlay`) abre el reproductor en modo video; "A continuación" (cola) abajo; botón "Pantalla apagada" (overlay negro, cualquier tecla lo quita); sin ecualizador en TV. Con overlays abiertos el fondo usa `focusProperties { canFocus = false }`. `tvClickable` = foco con escala, relleno y borde verde+blanco. Video en TV parte en 720p.
- Perfiles locales: `data/Profiles.kt` (lista en SharedPreferences "ritmo_profiles"; p0 usa las carpetas de siempre, los demás `filesDir/profiles/<id>`). Cada perfil: library.json, taste.json, last_session.json, home_cache.json. Cambiar de perfil reinicia la app. En TV, selector "¿Quién escucha?" al abrir si hay más de uno. Ajustes → Perfiles.
- Pendiente: transmitir a Chromecast/Google TV (Google Cast: media3-cast + play-services-cast-framework).
