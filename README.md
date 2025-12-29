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

### Search

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/search` | Search YouTube via Invidious API |

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

1. [x] Project scaffolding (go mod, folder structure, main entrypoint)
2. [x] Database connection (PostgreSQL, connection pooling, health check)
3. [x] Database migrations (users, tracks, videos tables)
4. [x] User registration endpoint (`POST /auth/register`)
5. [x] User login endpoint (`POST /auth/login`, JWT generation)
6. [x] JWT middleware (token validation, user context)
7. [x] Token refresh endpoint (`POST /auth/refresh`)
8. [x] Invite system (generate codes, validate on register)
9. [x] Search endpoint (`GET /search`, Invidious API proxy)
10. [x] yt-dlp integration (download audio/video, extract metadata)
11. [x] Download endpoint (`POST /download`)
12. [x] File serving endpoint (`GET /files/{id}`)
13. [x] Music library endpoints (`GET /library/music`, `DELETE /library/{id}`)
14. [x] Video library endpoints (`GET /library/videos`)
15. [x] Track metadata update endpoint (rename title/artist)
16. [x] Lyrics endpoint (`GET /lyrics`, Genius API integration)
17. [x] Playlist CRUD endpoints
18. [x] Dockerfile + docker-compose setup
19. [x] Rate limiting middleware
20. [x] Admin endpoints (manage users, invites)

### Android Phase 1: Foundation

21. [x] Remove Firebase dependencies
22. [x] API client setup (Retrofit, OkHttp with auth interceptor)
23. [x] Token storage (encrypted SharedPreferences)
24. [x] Login screen
25. [x] Register screen (with invite code)
26. [ ] Auth state management (logged in/out, token refresh)

### Android Phase 2: Music

27. [ ] Search screen (Invidious integration)
28. [ ] Download flow (request to backend, progress tracking)
29. [ ] Music library screen (list tracks from backend)
30. [ ] Track detail/edit screen (rename title/artist)
31. [ ] Music player service (background playback)
32. [ ] Now playing screen
33. [ ] Queue management UI
34. [ ] Playback controls (shuffle, repeat)
35. [ ] Notification controls

### Android Phase 3: Video

36. [ ] Video search integration
37. [ ] Video download flow (quality selection)
38. [ ] Video library screen
39. [ ] Video player screen
40. [ ] Floating miniplayer
41. [ ] Video playback controls (skip, fullscreen)

### Android Phase 4: Enhanced

42. [ ] Lyrics screen (fetch and display)
43. [ ] Playlist creation UI
44. [ ] Playlist management (add/remove tracks)
45. [ ] Playlist playback
46. [ ] Settings screen
47. [ ] Offline mode handling

## Legal Notice

This project is for personal use. Downloading copyrighted content may violate YouTube's Terms of Service and local laws. Users are responsible for ensuring their use complies with applicable regulations.

## License

Proprietary. All rights reserved.
