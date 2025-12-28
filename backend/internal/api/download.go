package api

import (
	"encoding/json"
	"log"
	"net/http"
	"os"

	"github.com/wpinrui/dovora2/backend/internal/db"
	"github.com/wpinrui/dovora2/backend/internal/ytdlp"
)

const defaultVideoQuality = "best"

type DownloadHandler struct {
	db         *db.DB
	downloader *ytdlp.Downloader
}

func NewDownloadHandler(database *db.DB, downloader *ytdlp.Downloader) *DownloadHandler {
	return &DownloadHandler{db: database, downloader: downloader}
}

type downloadRequest struct {
	VideoID string `json:"video_id"`
	Type    string `json:"type"` // "audio" or "video"
}

type downloadResponse struct {
	ID              string `json:"id"`
	YoutubeID       string `json:"youtube_id"`
	Title           string `json:"title"`
	Artist          string `json:"artist,omitempty"`
	Channel         string `json:"channel,omitempty"`
	DurationSeconds int    `json:"duration_seconds"`
	ThumbnailURL    string `json:"thumbnail_url"`
	FileSizeBytes   int64  `json:"file_size_bytes"`
	Type            string `json:"type"`
}

func (h *DownloadHandler) Download(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "user not found in context")
		return
	}

	var req downloadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.VideoID == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if req.Type != "audio" && req.Type != "video" {
		writeError(w, http.StatusBadRequest, "type must be 'audio' or 'video'")
		return
	}

	var result *ytdlp.DownloadResult
	var err error

	if req.Type == "audio" {
		result, err = h.downloader.DownloadAudio(r.Context(), req.VideoID)
	} else {
		result, err = h.downloader.DownloadVideo(r.Context(), req.VideoID)
	}

	if err != nil {
		log.Printf("Download failed for %s: %v", req.VideoID, err)
		writeError(w, http.StatusInternalServerError, "download failed")
		return
	}

	// Get file size
	fileInfo, err := os.Stat(result.FilePath)
	if err != nil {
		log.Printf("Failed to stat file %s: %v", result.FilePath, err)
		writeError(w, http.StatusInternalServerError, "failed to get file info")
		return
	}
	fileSize := fileInfo.Size()

	var response downloadResponse

	if req.Type == "audio" {
		track := &db.Track{
			UserID:          userID,
			YoutubeID:       result.Metadata.ID,
			Title:           result.Metadata.Title,
			Artist:          result.Metadata.Artist,
			DurationSeconds: result.Metadata.Duration,
			ThumbnailURL:    result.Metadata.Thumbnail,
			FilePath:        result.FilePath,
			FileSizeBytes:   fileSize,
		}

		// Use channel as fallback for artist
		if track.Artist == "" {
			track.Artist = result.Metadata.Channel
		}

		track, err = h.db.CreateTrack(r.Context(), track)
		if err != nil {
			log.Printf("Failed to save track: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to save track")
			return
		}

		response = downloadResponse{
			ID:              track.ID,
			YoutubeID:       track.YoutubeID,
			Title:           track.Title,
			Artist:          track.Artist,
			DurationSeconds: track.DurationSeconds,
			ThumbnailURL:    track.ThumbnailURL,
			FileSizeBytes:   track.FileSizeBytes,
			Type:            "audio",
		}
	} else {
		video := &db.Video{
			UserID:          userID,
			YoutubeID:       result.Metadata.ID,
			Title:           result.Metadata.Title,
			Channel:         result.Metadata.Channel,
			DurationSeconds: result.Metadata.Duration,
			ThumbnailURL:    result.Metadata.Thumbnail,
			FilePath:        result.FilePath,
			FileSizeBytes:   fileSize,
			Quality:         defaultVideoQuality,
		}

		video, err = h.db.CreateVideo(r.Context(), video)
		if err != nil {
			log.Printf("Failed to save video: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to save video")
			return
		}

		response = downloadResponse{
			ID:              video.ID,
			YoutubeID:       video.YoutubeID,
			Title:           video.Title,
			Channel:         video.Channel,
			DurationSeconds: video.DurationSeconds,
			ThumbnailURL:    video.ThumbnailURL,
			FileSizeBytes:   video.FileSizeBytes,
			Type:            "video",
		}
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(response)
}
