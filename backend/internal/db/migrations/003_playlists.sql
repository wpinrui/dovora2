-- Playlists table
CREATE TABLE IF NOT EXISTS playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON playlists(user_id);

-- Playlist tracks junction table (tracks in playlists with ordering)
CREATE TABLE IF NOT EXISTS playlist_tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(playlist_id, track_id)
);

CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist_id ON playlist_tracks(playlist_id);
CREATE INDEX IF NOT EXISTS idx_playlist_tracks_track_id ON playlist_tracks(track_id);
