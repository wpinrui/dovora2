package api

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/wpinrui/dovora2/backend/internal/db"
)

const timeFormatISO8601 = "2006-01-02T15:04:05Z"

type PlaylistHandler struct {
	db *db.DB
}

func NewPlaylistHandler(database *db.DB) *PlaylistHandler {
	return &PlaylistHandler{db: database}
}

type playlistResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	CreatedAt string `json:"created_at"`
	UpdatedAt string `json:"updated_at"`
}

type playlistWithTracksResponse struct {
	ID        string          `json:"id"`
	Name      string          `json:"name"`
	CreatedAt string          `json:"created_at"`
	UpdatedAt string          `json:"updated_at"`
	Tracks    []trackResponse `json:"tracks"`
}

type playlistsResponse struct {
	Playlists []playlistResponse `json:"playlists"`
}

type createPlaylistRequest struct {
	Name string `json:"name"`
}

type updatePlaylistRequest struct {
	Name string `json:"name"`
}

type addTrackRequest struct {
	TrackID string `json:"track_id"`
}

type reorderTracksRequest struct {
	TrackIDs []string `json:"track_ids"`
}

// verifyPlaylistOwnership checks that a playlist exists and belongs to the user.
// Returns the playlist if found, or writes an error response and returns nil.
func (h *PlaylistHandler) verifyPlaylistOwnership(ctx context.Context, w http.ResponseWriter, playlistID, userID string) *db.Playlist {
	playlist, err := h.db.GetPlaylistByID(ctx, playlistID, userID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			writeError(w, http.StatusNotFound, "playlist not found")
			return nil
		}
		log.Printf("Failed to get playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to verify playlist")
		return nil
	}
	return playlist
}

// HandlePlaylists routes requests to /playlists (list and create)
func (h *PlaylistHandler) HandlePlaylists(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		h.list(w, r)
	case http.MethodPost:
		h.create(w, r)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// list returns all playlists for the authenticated user
func (h *PlaylistHandler) list(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	playlists, err := h.db.GetPlaylistsByUserID(r.Context(), userID)
	if err != nil {
		log.Printf("Failed to get playlists for user %s: %v", userID, err)
		writeError(w, http.StatusInternalServerError, "failed to get playlists")
		return
	}

	response := playlistsResponse{
		Playlists: make([]playlistResponse, 0, len(playlists)),
	}

	for _, p := range playlists {
		response.Playlists = append(response.Playlists, playlistResponse{
			ID:        p.ID,
			Name:      p.Name,
			CreatedAt: p.CreatedAt.Format(timeFormatISO8601),
			UpdatedAt: p.UpdatedAt.Format(timeFormatISO8601),
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// create creates a new playlist
func (h *PlaylistHandler) create(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	var req createPlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.Name == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}

	playlist, err := h.db.CreatePlaylist(r.Context(), userID, req.Name)
	if err != nil {
		log.Printf("Failed to create playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to create playlist")
		return
	}

	response := playlistResponse{
		ID:        playlist.ID,
		Name:      playlist.Name,
		CreatedAt: playlist.CreatedAt.Format(timeFormatISO8601),
		UpdatedAt: playlist.UpdatedAt.Format(timeFormatISO8601),
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(response)
}

// Get returns a single playlist with its tracks
func (h *PlaylistHandler) Get(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract ID from URL path: /playlists/{id}
	id := strings.TrimPrefix(r.URL.Path, "/playlists/")
	if id == "" || id == r.URL.Path {
		writeError(w, http.StatusBadRequest, "id is required")
		return
	}

	// Remove any trailing path segments (for /tracks endpoints)
	if idx := strings.Index(id, "/"); idx != -1 {
		id = id[:idx]
	}

	playlist, err := h.db.GetPlaylistWithTracks(r.Context(), id, userID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			writeError(w, http.StatusNotFound, "playlist not found")
			return
		}
		log.Printf("Failed to get playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to get playlist")
		return
	}

	tracks := make([]trackResponse, 0, len(playlist.Tracks))
	for _, track := range playlist.Tracks {
		tracks = append(tracks, trackResponse{
			ID:              track.ID,
			YoutubeID:       track.YoutubeID,
			Title:           track.Title,
			Artist:          track.Artist,
			DurationSeconds: track.DurationSeconds,
			ThumbnailURL:    track.ThumbnailURL,
			FileSizeBytes:   track.FileSizeBytes,
			CreatedAt:       track.CreatedAt.Format(timeFormatISO8601),
		})
	}

	response := playlistWithTracksResponse{
		ID:        playlist.ID,
		Name:      playlist.Name,
		CreatedAt: playlist.CreatedAt.Format(timeFormatISO8601),
		UpdatedAt: playlist.UpdatedAt.Format(timeFormatISO8601),
		Tracks:    tracks,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// Update updates a playlist's name
func (h *PlaylistHandler) Update(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract ID from URL path: /playlists/{id}
	id := strings.TrimPrefix(r.URL.Path, "/playlists/")
	if id == "" || id == r.URL.Path {
		writeError(w, http.StatusBadRequest, "id is required")
		return
	}

	var req updatePlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.Name == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}

	playlist, err := h.db.UpdatePlaylist(r.Context(), id, userID, req.Name)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			writeError(w, http.StatusNotFound, "playlist not found")
			return
		}
		log.Printf("Failed to update playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to update playlist")
		return
	}

	response := playlistResponse{
		ID:        playlist.ID,
		Name:      playlist.Name,
		CreatedAt: playlist.CreatedAt.Format(timeFormatISO8601),
		UpdatedAt: playlist.UpdatedAt.Format(timeFormatISO8601),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// Delete deletes a playlist
func (h *PlaylistHandler) Delete(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract ID from URL path: /playlists/{id}
	id := strings.TrimPrefix(r.URL.Path, "/playlists/")
	if id == "" || id == r.URL.Path {
		writeError(w, http.StatusBadRequest, "id is required")
		return
	}

	err := h.db.DeletePlaylist(r.Context(), id, userID)
	if err != nil {
		if errors.Is(err, db.ErrNotFound) {
			writeError(w, http.StatusNotFound, "playlist not found")
			return
		}
		log.Printf("Failed to delete playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to delete playlist")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// AddTrack adds a track to a playlist
func (h *PlaylistHandler) AddTrack(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract playlist ID from URL path: /playlists/{id}/tracks
	path := strings.TrimPrefix(r.URL.Path, "/playlists/")
	parts := strings.Split(path, "/")
	if len(parts) < 2 || parts[1] != "tracks" {
		writeError(w, http.StatusBadRequest, "invalid path")
		return
	}
	playlistID := parts[0]

	if h.verifyPlaylistOwnership(r.Context(), w, playlistID, userID) == nil {
		return
	}

	var req addTrackRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.TrackID == "" {
		writeError(w, http.StatusBadRequest, "track_id is required")
		return
	}

	// Verify track belongs to user
	_, err := h.db.GetTrackByID(r.Context(), req.TrackID, userID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			writeError(w, http.StatusNotFound, "track not found")
			return
		}
		log.Printf("Failed to get track: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to verify track")
		return
	}

	err = h.db.AddTrackToPlaylist(r.Context(), playlistID, req.TrackID)
	if err != nil {
		log.Printf("Failed to add track to playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to add track to playlist")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// RemoveTrack removes a track from a playlist
func (h *PlaylistHandler) RemoveTrack(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract playlist ID and track ID from URL path: /playlists/{id}/tracks/{trackId}
	path := strings.TrimPrefix(r.URL.Path, "/playlists/")
	parts := strings.Split(path, "/")
	if len(parts) < 3 || parts[1] != "tracks" {
		writeError(w, http.StatusBadRequest, "invalid path")
		return
	}
	playlistID := parts[0]
	trackID := parts[2]

	if h.verifyPlaylistOwnership(r.Context(), w, playlistID, userID) == nil {
		return
	}

	err := h.db.RemoveTrackFromPlaylist(r.Context(), playlistID, trackID)
	if err != nil {
		if errors.Is(err, db.ErrNotFound) {
			writeError(w, http.StatusNotFound, "track not in playlist")
			return
		}
		log.Printf("Failed to remove track from playlist: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to remove track from playlist")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// ReorderTracks updates the order of tracks in a playlist
func (h *PlaylistHandler) ReorderTracks(w http.ResponseWriter, r *http.Request) {
	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract playlist ID from URL path: /playlists/{id}/tracks
	path := strings.TrimPrefix(r.URL.Path, "/playlists/")
	parts := strings.Split(path, "/")
	if len(parts) < 2 || parts[1] != "tracks" {
		writeError(w, http.StatusBadRequest, "invalid path")
		return
	}
	playlistID := parts[0]

	if h.verifyPlaylistOwnership(r.Context(), w, playlistID, userID) == nil {
		return
	}

	var req reorderTracksRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if len(req.TrackIDs) == 0 {
		writeError(w, http.StatusBadRequest, "track_ids is required")
		return
	}

	err := h.db.ReorderPlaylistTracks(r.Context(), playlistID, req.TrackIDs)
	if err != nil {
		log.Printf("Failed to reorder playlist tracks: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to reorder tracks")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// HandlePlaylist routes requests to the appropriate handler based on method
func (h *PlaylistHandler) HandlePlaylist(w http.ResponseWriter, r *http.Request) {
	// Check if this is a /playlists/{id}/tracks path
	path := strings.TrimPrefix(r.URL.Path, "/playlists/")
	if strings.Contains(path, "/tracks") {
		h.handlePlaylistTracks(w, r)
		return
	}

	switch r.Method {
	case http.MethodGet:
		h.Get(w, r)
	case http.MethodPut:
		h.Update(w, r)
	case http.MethodDelete:
		h.Delete(w, r)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// handlePlaylistTracks routes track-related requests
func (h *PlaylistHandler) handlePlaylistTracks(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/playlists/")
	parts := strings.Split(path, "/")

	// /playlists/{id}/tracks
	if len(parts) == 2 && parts[1] == "tracks" {
		switch r.Method {
		case http.MethodPost:
			h.AddTrack(w, r)
		case http.MethodPut:
			h.ReorderTracks(w, r)
		default:
			writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	// /playlists/{id}/tracks/{trackId}
	if len(parts) == 3 && parts[1] == "tracks" {
		switch r.Method {
		case http.MethodDelete:
			h.RemoveTrack(w, r)
		default:
			writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	writeError(w, http.StatusBadRequest, "invalid path")
}
