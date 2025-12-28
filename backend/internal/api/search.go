package api

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/wpinrui/dovora2/backend/internal/invidious"
)

type SearchHandler struct {
	invidiousClient *invidious.Client
}

func NewSearchHandler(invidiousClient *invidious.Client) *SearchHandler {
	return &SearchHandler{invidiousClient: invidiousClient}
}

type searchResultResponse struct {
	VideoID       string `json:"video_id"`
	Title         string `json:"title"`
	Author        string `json:"author"`
	Duration      int    `json:"duration"`
	ThumbnailURL  string `json:"thumbnail_url"`
	ViewCount     int64  `json:"view_count,omitempty"`
	PublishedText string `json:"published_text,omitempty"`
}

type searchResponse struct {
	Results []searchResultResponse `json:"results"`
}

func (h *SearchHandler) Search(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	query := r.URL.Query().Get("q")
	if query == "" {
		writeError(w, http.StatusBadRequest, "q parameter is required")
		return
	}

	searchType := r.URL.Query().Get("type")
	if searchType != "" && searchType != "music" && searchType != "video" {
		writeError(w, http.StatusBadRequest, "type must be 'music' or 'video'")
		return
	}

	// Invidious uses "video" for both music and video searches
	results, err := h.invidiousClient.Search(r.Context(), query, "video")
	if err != nil {
		log.Printf("Invidious search failed: %v", err)
		writeError(w, http.StatusBadGateway, "search service unavailable")
		return
	}

	response := searchResponse{
		Results: make([]searchResultResponse, 0, len(results)),
	}

	for _, result := range results {
		thumbnail := ""
		if len(result.VideoThumbnails) > 0 {
			// Prefer medium quality thumbnail
			for _, t := range result.VideoThumbnails {
				if t.Quality == "medium" {
					thumbnail = t.URL
					break
				}
			}
			// Fallback to first available
			if thumbnail == "" {
				thumbnail = result.VideoThumbnails[0].URL
			}
		}

		response.Results = append(response.Results, searchResultResponse{
			VideoID:       result.VideoID,
			Title:         result.Title,
			Author:        result.Author,
			Duration:      result.LengthSeconds,
			ThumbnailURL:  thumbnail,
			ViewCount:     result.ViewCount,
			PublishedText: result.PublishedText,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}
