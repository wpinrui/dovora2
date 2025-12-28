package api

import (
	"errors"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/wpinrui/dovora2/backend/internal/db"
)

type FileHandler struct {
	db *db.DB
}

func NewFileHandler(database *db.DB) *FileHandler {
	return &FileHandler{db: database}
}

func (h *FileHandler) ServeFile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	// Extract ID from URL path: /files/{id}
	id := strings.TrimPrefix(r.URL.Path, "/files/")
	if id == "" || id == r.URL.Path {
		writeError(w, http.StatusBadRequest, "file id is required")
		return
	}

	// Try to find as track first
	track, err := h.db.GetTrackByID(r.Context(), id, userID)
	if err == nil {
		h.serveMediaFile(w, r, track.FilePath, track.Title+".m4a", "audio/mp4")
		return
	}

	// If not found as track, check if it's a "not found" error
	if !errors.Is(err, pgx.ErrNoRows) {
		log.Printf("Failed to query track: %v", err)
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	// Try to find as video
	video, err := h.db.GetVideoByID(r.Context(), id, userID)
	if err == nil {
		h.serveMediaFile(w, r, video.FilePath, video.Title+".mp4", "video/mp4")
		return
	}

	if errors.Is(err, pgx.ErrNoRows) {
		writeError(w, http.StatusNotFound, "file not found")
		return
	}

	log.Printf("Failed to query video: %v", err)
	writeError(w, http.StatusInternalServerError, "database error")
}

func (h *FileHandler) serveMediaFile(w http.ResponseWriter, r *http.Request, filePath, filename, contentType string) {
	// Check file exists
	fileInfo, err := os.Stat(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			log.Printf("File not found on disk: %s", filePath)
			writeError(w, http.StatusNotFound, "file not found on disk")
			return
		}
		log.Printf("Failed to stat file: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to access file")
		return
	}

	// Open file
	file, err := os.Open(filePath)
	if err != nil {
		log.Printf("Failed to open file: %v", err)
		writeError(w, http.StatusInternalServerError, "failed to open file")
		return
	}
	defer file.Close()

	// Sanitize filename for Content-Disposition header
	safeFilename := sanitizeFilename(filename)

	// Set headers (http.ServeContent handles Content-Length and Accept-Ranges)
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Disposition", "attachment; filename=\""+safeFilename+"\"")

	// Use http.ServeContent for proper range request support
	http.ServeContent(w, r, safeFilename, fileInfo.ModTime(), file)
}

func sanitizeFilename(filename string) string {
	// Remove path separators and other problematic characters
	filename = filepath.Base(filename)

	// Replace characters that are problematic in filenames or HTTP headers
	replacer := strings.NewReplacer(
		"\"", "'",
		"\\", "_",
		"/", "_",
		":", "_",
		"*", "_",
		"?", "_",
		"<", "_",
		">", "_",
		"|", "_",
	)

	return replacer.Replace(filename)
}
