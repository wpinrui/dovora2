package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/wpinrui/dovora2/backend/internal/db"
)

type LibraryHandler struct {
	db *db.DB
}

func NewLibraryHandler(database *db.DB) *LibraryHandler {
	return &LibraryHandler{db: database}
}

type trackResponse struct {
	ID              string `json:"id"`
	YoutubeID       string `json:"youtube_id"`
	Title           string `json:"title"`
	Artist          string `json:"artist"`
	DurationSeconds int    `json:"duration_seconds"`
	ThumbnailURL    string `json:"thumbnail_url"`
	FileSizeBytes   int64  `json:"file_size_bytes"`
	CreatedAt       string `json:"created_at"`
}

type libraryResponse struct {
	Tracks []trackResponse `json:"tracks"`
}

type videoResponse struct {
	ID              string `json:"id"`
	YoutubeID       string `json:"youtube_id"`
	Title           string `json:"title"`
	Channel         string `json:"channel"`
	DurationSeconds int    `json:"duration_seconds"`
	ThumbnailURL    string `json:"thumbnail_url"`
	FileSizeBytes   int64  `json:"file_size_bytes"`
	Quality         string `json:"quality"`
	CreatedAt       string `json:"created_at"`
}

type videoLibraryResponse struct {
	Videos []videoResponse `json:"videos"`
}

func (h *LibraryHandler) GetMusic(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	tracks, err := h.db.GetTracksByUserID(r.Context(), userID)
	if err != nil {
		log.Printf("Failed to get tracks for user %s: %v", userID, err)
		writeError(w, http.StatusInternalServerError, "failed to get library")
		return
	}

	response := libraryResponse{
		Tracks: make([]trackResponse, 0, len(tracks)),
	}

	for _, track := range tracks {
		response.Tracks = append(response.Tracks, trackResponse{
			ID:              track.ID,
			YoutubeID:       track.YoutubeID,
			Title:           track.Title,
			Artist:          track.Artist,
			DurationSeconds: track.DurationSeconds,
			ThumbnailURL:    track.ThumbnailURL,
			FileSizeBytes:   track.FileSizeBytes,
			CreatedAt:       track.CreatedAt.Format("2006-01-02T15:04:05Z"),
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *LibraryHandler) GetVideos(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	videos, err := h.db.GetVideosByUserID(r.Context(), userID)
	if err != nil {
		log.Printf("Failed to get videos for user %s: %v", userID, err)
		writeError(w, http.StatusInternalServerError, "failed to get video library")
		return
	}

	response := videoLibraryResponse{
		Videos: make([]videoResponse, 0, len(videos)),
	}

	for _, video := range videos {
		response.Videos = append(response.Videos, videoResponse{
			ID:              video.ID,
			YoutubeID:       video.YoutubeID,
			Title:           video.Title,
			Channel:         video.Channel,
			DurationSeconds: video.DurationSeconds,
			ThumbnailURL:    video.ThumbnailURL,
			FileSizeBytes:   video.FileSizeBytes,
			Quality:         video.Quality,
			CreatedAt:       video.CreatedAt.Format("2006-01-02T15:04:05Z"),
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

type updateTrackRequest struct {
	Title  string `json:"title"`
	Artist string `json:"artist"`
}

func (h *LibraryHandler) UpdateTrack(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract ID from URL path: /tracks/{id}
	id := strings.TrimPrefix(r.URL.Path, "/tracks/")
	if id == "" || id == r.URL.Path {
		writeError(w, http.StatusBadRequest, "id is required")
		return
	}

	var req updateTrackRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.Title == "" && req.Artist == "" {
		writeError(w, http.StatusBadRequest, "title or artist is required")
		return
	}

	// Get existing track to preserve unchanged fields
	existingTrack, err := h.db.GetTrackByID(r.Context(), id, userID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			writeError(w, http.StatusNotFound, "track not found")
			return
		}
		log.Printf("Failed to get track: %v", err)
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	// Use existing values if not provided
	title := req.Title
	if title == "" {
		title = existingTrack.Title
	}
	artist := req.Artist
	if artist == "" {
		artist = existingTrack.Artist
	}

	track, err := h.db.UpdateTrack(r.Context(), id, userID, title, artist)
	if err != nil {
		log.Printf("Failed to update track: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to update track")
		return
	}

	response := trackResponse{
		ID:              track.ID,
		YoutubeID:       track.YoutubeID,
		Title:           track.Title,
		Artist:          track.Artist,
		DurationSeconds: track.DurationSeconds,
		ThumbnailURL:    track.ThumbnailURL,
		FileSizeBytes:   track.FileSizeBytes,
		CreatedAt:       track.CreatedAt.Format("2006-01-02T15:04:05Z"),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *LibraryHandler) DeleteItem(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract ID from URL path: /library/{id}
	id := strings.TrimPrefix(r.URL.Path, "/library/")
	if id == "" || id == r.URL.Path {
		writeError(w, http.StatusBadRequest, "id is required")
		return
	}

	// Try to delete as track first
	filePath, err := h.db.DeleteTrack(r.Context(), id, userID)
	if err == nil {
		// Successfully deleted track, now delete file from disk
		if err := os.Remove(filePath); err != nil && !os.IsNotExist(err) {
			log.Printf("Failed to delete file %s: %v", filePath, err)
		}

		w.WriteHeader(http.StatusNoContent)
		return
	}

	// If not found as track, check if it's a "not found" error
	if !errors.Is(err, pgx.ErrNoRows) {
		log.Printf("Failed to delete track: %v", err)
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	// Track not found - try to delete as video
	filePath, err = h.db.DeleteVideo(r.Context(), id, userID)
	if err == nil {
		// Successfully deleted video, now delete file from disk
		if err := os.Remove(filePath); err != nil && !os.IsNotExist(err) {
			log.Printf("Failed to delete file %s: %v", filePath, err)
		}

		w.WriteHeader(http.StatusNoContent)
		return
	}

	// If not found as video, check if it's a "not found" error
	if !errors.Is(err, pgx.ErrNoRows) {
		log.Printf("Failed to delete video: %v", err)
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	// Item not found in tracks or videos
	writeError(w, http.StatusNotFound, "item not found")
}
