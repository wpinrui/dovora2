# dovora

A private YouTube media downloader and player for Android, powered by a Go backend.

Download music and videos once, keep them forever. Your media, your library, your rules.

## Overview

dovora is a multi-user media management system consisting of:

- **Backend**: Go REST API server handling authentication, downloads, and media serving
- **Android App**: Kotlin/Jetpack Compose app for searching, downloading, and playing media
- **Database**: PostgreSQL for user data, libraries, and metadata

## Features

### Core

- **YouTube Search**: Search for music and videos via Invidious API
- **Download**: Audio (M4A) and video downloads via yt-dlp
- **Per-User Libraries**: Each user has their own isolated music and video library
- **Local Playback**: Media stored on device for offline access
- **Lyrics**: Real-time lyrics fetching from Genius API

### Music

- Background playback with notification controls
- Queue management with drag-to-reorder
- Shuffle and repeat modes
- Playback history
- Custom track renaming

### Video

- Video library with quality selection
- Floating miniplayer with corner snapping
- Skip controls (10s forward/back)
- Video history

### Planned

- [ ] User-created playlists
- [ ] Playlist sharing between users (optional)

## Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌──────────────┐
│  Android App    │ ──────► │   Go Backend    │ ──────► │  PostgreSQL  │
│  (Kotlin/       │  HTTPS  │   (REST API)    │         │  (User data, │
│   Compose)      │ ◄────── │                 │         │   metadata)  │
└─────────────────┘         └────────┬────────┘         └──────────────┘
                                     │
                                     ▼
                            ┌─────────────────┐
                            │    yt-dlp +     │
                            │    ffmpeg       │
                            └─────────────────┘
```

### Data Flow

1. User searches on Android app (queries Invidious)
2. User selects track/video to download
3. App sends download request to backend with JWT token
4. Backend validates user, downloads via yt-dlp
5. Backend stores metadata in PostgreSQL (linked to user)
6. App downloads file from backend, stores locally on device
7. Playback happens locally on device

## Tech Stack

### Backend

- **Language**: Go
- **Framework**: TBD (likely Gin or Echo)
- **Database**: PostgreSQL
- **Auth**: JWT tokens
- **Download**: yt-dlp + ffmpeg
- **Container**: Docker

### Android

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Networking**: Retrofit + OkHttp
- **Playback**: Media3/ExoPlayer
- **Images**: Coil
- **Async**: Kotlin Coroutines

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Register new user (invite-only, see below) |
| POST | `/auth/login` | Login, returns JWT token |
| POST | `/auth/refresh` | Refresh JWT token |

### Media

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/download` | Queue a download (audio/video) |
| GET | `/library/music` | Get user's music library |
| GET | `/library/videos` | Get user's video library |
| GET | `/files/{id}` | Download a file to device |
| DELETE | `/library/{id}` | Remove item from library |

### Lyrics

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/lyrics` | Fetch lyrics for a track |

### Playlists (Planned)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/playlists` | Get user's playlists |
| POST | `/playlists` | Create playlist |
| PUT | `/playlists/{id}` | Update playlist |
| DELETE | `/playlists/{id}` | Delete playlist |

## User Access

> **Note**: User access model is still being determined.

Since the backend runs on a paid server and yt-dlp downloads consume resources, user access must be managed carefully. Current direction:

- **Invite-only**: New users require an invite code from existing users or admin
- Invite codes may be limited per user
- Admin can create unlimited invites

Alternative models under consideration:
- Admin approval for new registrations
- Open registration with usage limits

## Deployment

### Requirements

- Docker and Docker Compose
- Server with:
  - yt-dlp installed (or included in container)
  - ffmpeg installed (or included in container)
  - Sufficient storage for temporary downloads
- PostgreSQL database
- HTTPS (recommended for production)

### Docker Compose

```yaml
version: '3.8'

services:
  dovora:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=postgres://user:pass@db:5432/dovora
      - JWT_SECRET=your-secret-key
      - GENIUS_API_KEY=your-genius-key
    depends_on:
      - db
    volumes:
      - downloads:/app/downloads

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_USER=user
      - POSTGRES_PASSWORD=pass
      - POSTGRES_DB=dovora
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  downloads:
  pgdata:
```

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DATABASE_URL` | PostgreSQL connection string | Yes |
| `JWT_SECRET` | Secret key for JWT signing | Yes |
| `GENIUS_API_KEY` | Genius API key for lyrics | Yes |
| `PORT` | Server port (default: 8080) | No |
| `MAX_FILE_SIZE_MB` | Max download size, 0 = unlimited | No |

## Project Structure

```
dovora/
├── backend/                 # Go backend
│   ├── cmd/
│   │   └── server/         # Main entrypoint
│   ├── internal/
│   │   ├── api/            # HTTP handlers
│   │   ├── auth/           # JWT authentication
│   │   ├── db/             # Database models & queries
│   │   ├── download/       # yt-dlp integration
│   │   └── lyrics/         # Genius API client
│   ├── Dockerfile
│   └── go.mod
│
├── app/                     # Android application
│   ├── src/main/
│   │   ├── java/com/.../dovora/
│   │   │   ├── data/       # API clients, repositories
│   │   │   ├── ui/         # Compose screens
│   │   │   └── playback/   # Media playback
│   │   └── res/
│   └── build.gradle.kts
│
├── docker-compose.yml
└── README.md
```

## Development

### Backend

```bash
cd backend
go mod download
go run cmd/server/main.go
```

### Android

1. Open `app/` in Android Studio
2. Create `secrets.properties`:
   ```properties
   backendBaseUrl=https://your-server.com
   ```
3. Build and run

## Roadmap

### Backend

- [x] Project scaffolding (go mod, folder structure, main entrypoint)
- [ ] Database connection (PostgreSQL, connection pooling, health check)
- [ ] Database migrations (users, tracks, videos tables)
- [ ] User registration endpoint (`POST /auth/register`)
- [ ] User login endpoint (`POST /auth/login`, JWT generation)
- [ ] JWT middleware (token validation, user context)
- [ ] Token refresh endpoint (`POST /auth/refresh`)
- [ ] Invite system (generate codes, validate on register)
- [ ] yt-dlp integration (download audio/video, extract metadata)
- [ ] Download endpoint (`POST /download`)
- [ ] File serving endpoint (`GET /files/{id}`)
- [ ] Music library endpoints (`GET /library/music`, `DELETE /library/{id}`)
- [ ] Video library endpoints (`GET /library/videos`)
- [ ] Track metadata update endpoint (rename title/artist)
- [ ] Lyrics endpoint (`GET /lyrics`, Genius API integration)
- [ ] Playlist CRUD endpoints
- [ ] Dockerfile + docker-compose setup
- [ ] Rate limiting middleware
- [ ] Admin endpoints (manage users, invites)

### Android Phase 1: Foundation

- [ ] Remove Firebase dependencies
- [ ] API client setup (Retrofit, OkHttp with auth interceptor)
- [ ] Token storage (encrypted SharedPreferences)
- [ ] Login screen
- [ ] Register screen (with invite code)
- [ ] Auth state management (logged in/out, token refresh)

### Android Phase 2: Music

- [ ] Search screen (Invidious integration)
- [ ] Download flow (request to backend, progress tracking)
- [ ] Music library screen (list tracks from backend)
- [ ] Track detail/edit screen (rename title/artist)
- [ ] Music player service (background playback)
- [ ] Now playing screen
- [ ] Queue management UI
- [ ] Playback controls (shuffle, repeat)
- [ ] Notification controls

### Android Phase 3: Video

- [ ] Video search integration
- [ ] Video download flow (quality selection)
- [ ] Video library screen
- [ ] Video player screen
- [ ] Floating miniplayer
- [ ] Video playback controls (skip, fullscreen)

### Android Phase 4: Enhanced

- [ ] Lyrics screen (fetch and display)
- [ ] Playlist creation UI
- [ ] Playlist management (add/remove tracks)
- [ ] Playlist playback
- [ ] Settings screen
- [ ] Offline mode handling

## Legal Notice

This project is for personal use. Downloading copyrighted content may violate YouTube's Terms of Service and local laws. Users are responsible for ensuring their use complies with applicable regulations.

## License

Proprietary. All rights reserved.
