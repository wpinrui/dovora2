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

	// Track not found - item doesn't exist for this user
	writeError(w, http.StatusNotFound, "item not found")
}
