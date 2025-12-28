package db

import (
	"context"
	"time"
)

// Track represents a music track in a user's library
type Track struct {
	ID              string
	UserID          string
	YoutubeID       string
	Title           string
	Artist          string
	DurationSeconds int
	ThumbnailURL    string
	FilePath        string
	FileSizeBytes   int64
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// Video represents a video in a user's library
type Video struct {
	ID              string
	UserID          string
	YoutubeID       string
	Title           string
	Channel         string
	DurationSeconds int
	ThumbnailURL    string
	FilePath        string
	FileSizeBytes   int64
	Quality         string
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// CreateTrack inserts a new track into the database
func (db *DB) CreateTrack(ctx context.Context, track *Track) (*Track, error) {
	query := `
		INSERT INTO tracks (user_id, youtube_id, title, artist, duration_seconds, thumbnail_url, file_path, file_size_bytes)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		ON CONFLICT (user_id, youtube_id) DO UPDATE SET
			title = EXCLUDED.title,
			artist = EXCLUDED.artist,
			duration_seconds = EXCLUDED.duration_seconds,
			thumbnail_url = EXCLUDED.thumbnail_url,
			file_path = EXCLUDED.file_path,
			file_size_bytes = EXCLUDED.file_size_bytes,
			updated_at = NOW()
		RETURNING id, created_at, updated_at
	`

	err := db.Pool.QueryRow(ctx, query,
		track.UserID,
		track.YoutubeID,
		track.Title,
		track.Artist,
		track.DurationSeconds,
		track.ThumbnailURL,
		track.FilePath,
		track.FileSizeBytes,
	).Scan(&track.ID, &track.CreatedAt, &track.UpdatedAt)

	if err != nil {
		return nil, err
	}

	return track, nil
}

// CreateVideo inserts a new video into the database
func (db *DB) CreateVideo(ctx context.Context, video *Video) (*Video, error) {
	query := `
		INSERT INTO videos (user_id, youtube_id, title, channel, duration_seconds, thumbnail_url, file_path, file_size_bytes, quality)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		ON CONFLICT (user_id, youtube_id) DO UPDATE SET
			title = EXCLUDED.title,
			channel = EXCLUDED.channel,
			duration_seconds = EXCLUDED.duration_seconds,
			thumbnail_url = EXCLUDED.thumbnail_url,
			file_path = EXCLUDED.file_path,
			file_size_bytes = EXCLUDED.file_size_bytes,
			quality = EXCLUDED.quality,
			updated_at = NOW()
		RETURNING id, created_at, updated_at
	`

	err := db.Pool.QueryRow(ctx, query,
		video.UserID,
		video.YoutubeID,
		video.Title,
		video.Channel,
		video.DurationSeconds,
		video.ThumbnailURL,
		video.FilePath,
		video.FileSizeBytes,
		video.Quality,
	).Scan(&video.ID, &video.CreatedAt, &video.UpdatedAt)

	if err != nil {
		return nil, err
	}

	return video, nil
}

// GetTrackByID retrieves a track by ID for a specific user
func (db *DB) GetTrackByID(ctx context.Context, trackID, userID string) (*Track, error) {
	query := `
		SELECT id, user_id, youtube_id, title, artist, duration_seconds, thumbnail_url, file_path, file_size_bytes, created_at, updated_at
		FROM tracks
		WHERE id = $1 AND user_id = $2
	`

	track := &Track{}
	err := db.Pool.QueryRow(ctx, query, trackID, userID).Scan(
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

	return track, nil
}

// GetVideoByID retrieves a video by ID for a specific user
func (db *DB) GetVideoByID(ctx context.Context, videoID, userID string) (*Video, error) {
	query := `
		SELECT id, user_id, youtube_id, title, channel, duration_seconds, thumbnail_url, file_path, file_size_bytes, quality, created_at, updated_at
		FROM videos
		WHERE id = $1 AND user_id = $2
	`

	video := &Video{}
	err := db.Pool.QueryRow(ctx, query, videoID, userID).Scan(
		&video.ID,
		&video.UserID,
		&video.YoutubeID,
		&video.Title,
		&video.Channel,
		&video.DurationSeconds,
		&video.ThumbnailURL,
		&video.FilePath,
		&video.FileSizeBytes,
		&video.Quality,
		&video.CreatedAt,
		&video.UpdatedAt,
	)

	if err != nil {
		return nil, err
	}

	return video, nil
}

// GetTracksByUserID retrieves all tracks for a user, ordered by most recent first
func (db *DB) GetTracksByUserID(ctx context.Context, userID string) ([]Track, error) {
	query := `
		SELECT id, user_id, youtube_id, title, artist, duration_seconds, thumbnail_url, file_path, file_size_bytes, created_at, updated_at
		FROM tracks
		WHERE user_id = $1
		ORDER BY created_at DESC
	`

	rows, err := db.Pool.Query(ctx, query, userID)
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

	return tracks, nil
}

// DeleteTrack deletes a track by ID for a specific user and returns the file path
func (db *DB) DeleteTrack(ctx context.Context, trackID, userID string) (string, error) {
	query := `
		DELETE FROM tracks
		WHERE id = $1 AND user_id = $2
		RETURNING file_path
	`

	var filePath string
	err := db.Pool.QueryRow(ctx, query, trackID, userID).Scan(&filePath)
	if err != nil {
		return "", err
	}

	return filePath, nil
}

// GetVideosByUserID retrieves all videos for a user, ordered by most recent first
func (db *DB) GetVideosByUserID(ctx context.Context, userID string) ([]Video, error) {
	query := `
		SELECT id, user_id, youtube_id, title, channel, duration_seconds, thumbnail_url, file_path, file_size_bytes, quality, created_at, updated_at
		FROM videos
		WHERE user_id = $1
		ORDER BY created_at DESC
	`

	rows, err := db.Pool.Query(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var videos []Video
	for rows.Next() {
		var video Video
		err := rows.Scan(
			&video.ID,
			&video.UserID,
			&video.YoutubeID,
			&video.Title,
			&video.Channel,
			&video.DurationSeconds,
			&video.ThumbnailURL,
			&video.FilePath,
			&video.FileSizeBytes,
			&video.Quality,
			&video.CreatedAt,
			&video.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		videos = append(videos, video)
	}

	if err := rows.Err(); err != nil {
		return nil, err
	}

	return videos, nil
}

// DeleteVideo deletes a video by ID for a specific user and returns the file path
func (db *DB) DeleteVideo(ctx context.Context, videoID, userID string) (string, error) {
	query := `
		DELETE FROM videos
		WHERE id = $1 AND user_id = $2
		RETURNING file_path
	`

	var filePath string
	err := db.Pool.QueryRow(ctx, query, videoID, userID).Scan(&filePath)
	if err != nil {
		return "", err
	}

	return filePath, nil
}
