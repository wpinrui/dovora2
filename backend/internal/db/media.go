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
