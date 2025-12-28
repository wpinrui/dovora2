package db

import (
	"context"
	"time"
)

// Playlist represents a user's playlist
type Playlist struct {
	ID        string
	UserID    string
	Name      string
	CreatedAt time.Time
	UpdatedAt time.Time
}

// PlaylistWithTracks represents a playlist with its tracks
type PlaylistWithTracks struct {
	Playlist
	Tracks []Track
}

// CreatePlaylist creates a new playlist for a user
func (db *DB) CreatePlaylist(ctx context.Context, userID, name string) (*Playlist, error) {
	query := `
		INSERT INTO playlists (user_id, name)
		VALUES ($1, $2)
		RETURNING id, user_id, name, created_at, updated_at
	`

	playlist := &Playlist{}
	err := db.Pool.QueryRow(ctx, query, userID, name).Scan(
		&playlist.ID,
		&playlist.UserID,
		&playlist.Name,
		&playlist.CreatedAt,
		&playlist.UpdatedAt,
	)

	if err != nil {
		return nil, err
	}

	return playlist, nil
}

// GetPlaylistsByUserID retrieves all playlists for a user
func (db *DB) GetPlaylistsByUserID(ctx context.Context, userID string) ([]Playlist, error) {
	query := `
		SELECT id, user_id, name, created_at, updated_at
		FROM playlists
		WHERE user_id = $1
		ORDER BY created_at DESC
	`

	rows, err := db.Pool.Query(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var playlists []Playlist
	for rows.Next() {
		var p Playlist
		err := rows.Scan(
			&p.ID,
			&p.UserID,
			&p.Name,
			&p.CreatedAt,
			&p.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		playlists = append(playlists, p)
	}

	if err := rows.Err(); err != nil {
		return nil, err
	}

	return playlists, nil
}

// GetPlaylistByID retrieves a playlist by ID for a specific user
func (db *DB) GetPlaylistByID(ctx context.Context, playlistID, userID string) (*Playlist, error) {
	query := `
		SELECT id, user_id, name, created_at, updated_at
		FROM playlists
		WHERE id = $1 AND user_id = $2
	`

	playlist := &Playlist{}
	err := db.Pool.QueryRow(ctx, query, playlistID, userID).Scan(
		&playlist.ID,
		&playlist.UserID,
		&playlist.Name,
		&playlist.CreatedAt,
		&playlist.UpdatedAt,
	)

	if err != nil {
		return nil, err
	}

	return playlist, nil
}

// GetPlaylistWithTracks retrieves a playlist with all its tracks
func (db *DB) GetPlaylistWithTracks(ctx context.Context, playlistID, userID string) (*PlaylistWithTracks, error) {
	// First get the playlist
	playlist, err := db.GetPlaylistByID(ctx, playlistID, userID)
	if err != nil {
		return nil, err
	}

	// Then get the tracks in order
	query := `
		SELECT t.id, t.user_id, t.youtube_id, t.title, t.artist, t.duration_seconds,
		       t.thumbnail_url, t.file_path, t.file_size_bytes, t.created_at, t.updated_at
		FROM tracks t
		INNER JOIN playlist_tracks pt ON t.id = pt.track_id
		WHERE pt.playlist_id = $1
		ORDER BY pt.position ASC
	`

	rows, err := db.Pool.Query(ctx, query, playlistID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tracks []Track
	for rows.Next() {
		var track Track
		err := rows.Scan(
			&track.ID,
			&track.UserID,
			&track.YoutubeID,
			&track.Title,
			&track.Artist,
			&track.DurationSeconds,
			&track.ThumbnailURL,
			&track.FilePath,
			&track.FileSizeBytes,
			&track.CreatedAt,
			&track.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		tracks = append(tracks, track)
	}

	if err := rows.Err(); err != nil {
		return nil, err
	}

	return &PlaylistWithTracks{
		Playlist: *playlist,
		Tracks:   tracks,
	}, nil
}

// UpdatePlaylist updates a playlist's name
func (db *DB) UpdatePlaylist(ctx context.Context, playlistID, userID, name string) (*Playlist, error) {
	query := `
		UPDATE playlists
		SET name = $3, updated_at = NOW()
		WHERE id = $1 AND user_id = $2
		RETURNING id, user_id, name, created_at, updated_at
	`

	playlist := &Playlist{}
	err := db.Pool.QueryRow(ctx, query, playlistID, userID, name).Scan(
		&playlist.ID,
		&playlist.UserID,
		&playlist.Name,
		&playlist.CreatedAt,
		&playlist.UpdatedAt,
	)

	if err != nil {
		return nil, err
	}

	return playlist, nil
}

// DeletePlaylist deletes a playlist by ID for a specific user
func (db *DB) DeletePlaylist(ctx context.Context, playlistID, userID string) error {
	query := `
		DELETE FROM playlists
		WHERE id = $1 AND user_id = $2
	`

	result, err := db.Pool.Exec(ctx, query, playlistID, userID)
	if err != nil {
		return err
	}

	if result.RowsAffected() == 0 {
		return ErrNotFound
	}

	return nil
}

// AddTrackToPlaylist adds a track to a playlist at the end
func (db *DB) AddTrackToPlaylist(ctx context.Context, playlistID, trackID string) error {
	// Get the next position
	var maxPos *int
	err := db.Pool.QueryRow(ctx, `
		SELECT MAX(position) FROM playlist_tracks WHERE playlist_id = $1
	`, playlistID).Scan(&maxPos)
	if err != nil {
		return err
	}

	nextPos := 0
	if maxPos != nil {
		nextPos = *maxPos + 1
	}

	query := `
		INSERT INTO playlist_tracks (playlist_id, track_id, position)
		VALUES ($1, $2, $3)
		ON CONFLICT (playlist_id, track_id) DO NOTHING
	`

	_, err = db.Pool.Exec(ctx, query, playlistID, trackID, nextPos)
	return err
}

// RemoveTrackFromPlaylist removes a track from a playlist
func (db *DB) RemoveTrackFromPlaylist(ctx context.Context, playlistID, trackID string) error {
	query := `
		DELETE FROM playlist_tracks
		WHERE playlist_id = $1 AND track_id = $2
	`

	result, err := db.Pool.Exec(ctx, query, playlistID, trackID)
	if err != nil {
		return err
	}

	if result.RowsAffected() == 0 {
		return ErrNotFound
	}

	return nil
}

// ReorderPlaylistTracks updates the positions of tracks in a playlist
func (db *DB) ReorderPlaylistTracks(ctx context.Context, playlistID string, trackIDs []string) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	// Update position for each track
	for i, trackID := range trackIDs {
		_, err := tx.Exec(ctx, `
			UPDATE playlist_tracks
			SET position = $3
			WHERE playlist_id = $1 AND track_id = $2
		`, playlistID, trackID, i)
		if err != nil {
			return err
		}
	}

	return tx.Commit(ctx)
}
