# Wax

A weekly album recommendation app with an animated vinyl turntable UI, built natively for Android with Jetpack Compose and Spotify integration.

## Features

- **Weekly curated album recommendation** — a new album surfaces every week, pulled from your Spotify library
- **Animated vinyl turntable** — album-color-reactive disc with dynamic Palette extraction, tonearm, and three swappable skins
- **Spotify integration** — OAuth 2.0 PKCE flow, no client secret required
- **Lock screen media controls** — MediaStyle notification mirrors Spotify playback state with album art
- **Track detection and highlighting** — MediaSession listener identifies the currently playing track and highlights it in the tracklist
- **Album history** — every weekly album is saved locally with Room; browse and revisit past picks
- **Swipeable tracklist** — bottom sheet with currently playing indicator and equalizer animation
- **Turntable skins** — Dark, Vintage Wood, and Minimalist themes
- **Weekly push notification** — WorkManager schedules a Monday morning notification when a new album is ready
- **Daydream screen saver** — Android Dream integration shows a gently spinning vinyl while the device is charging

## Tech Stack

| Layer | Libraries |
|---|---|
| UI | Jetpack Compose, Material3, Coil |
| Architecture | Clean Architecture (core / data / domain / presentation), ViewModel, StateFlow |
| DI | Hilt |
| Networking | Retrofit, OkHttp, Moshi |
| Storage | Room, DataStore Preferences |
| Background | WorkManager |
| Media | MediaSession API, MediaSessionCompat, NotificationCompat.MediaStyle |
| Auth | Spotify OAuth 2.0 PKCE |
| Colors | Palette API |
| Build | AGP 8.9.1 · Kotlin 2.0.21 · minSdk 26 · targetSdk 36 |

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/your-username/wax.git
cd wax
```

### 2. Configure Spotify credentials

Create a `local.properties` file in the project root (next to `gradle.properties`) and add:

```properties
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_REDIRECT_URI=your_redirect_uri
```

### 3. Register your redirect URI

In the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard):

1. Open your app (or create one)
2. Go to **Edit Settings**
3. Add your redirect URI under **Redirect URIs** — it must match `SPOTIFY_REDIRECT_URI` exactly

### 4. Grant notification access

The track detection feature requires **Notification Listener** access:

> Settings → Apps → Special app access → Notification access → Wax → Enable

The app will prompt you on first launch.

### 5. Build and run

Open in Android Studio Ladybug or later and run on a device or emulator with **Android 8.0+ (API 26+)**.

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Spotify Web API requests |
| `POST_NOTIFICATIONS` | Weekly album notification |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Lock screen media controls |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Track detection via MediaSession |
| `BIND_DREAM_SERVICE` | Daydream screen saver |

## Screenshots

Coming soon.

## License

MIT
