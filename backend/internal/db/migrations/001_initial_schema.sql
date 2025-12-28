-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Tracks table (music library)
CREATE TABLE IF NOT EXISTS tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    youtube_id VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    artist VARCHAR(500),
    duration_seconds INTEGER,
    thumbnail_url TEXT,
    file_path TEXT NOT NULL,
    file_size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, youtube_id)
);

CREATE INDEX IF NOT EXISTS idx_tracks_user_id ON tracks(user_id);

-- Videos table
CREATE TABLE IF NOT EXISTS videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    youtube_id VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    channel VARCHAR(500),
    duration_seconds INTEGER,
    thumbnail_url TEXT,
    file_path TEXT NOT NULL,
    file_size_bytes BIGINT,
    quality VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, youtube_id)
);

CREATE INDEX IF NOT EXISTS idx_videos_user_id ON videos(user_id);
