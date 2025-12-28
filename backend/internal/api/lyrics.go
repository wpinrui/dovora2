package api

import (
	"encoding/json"
	"net/http"

	"github.com/wpinrui/dovora2/backend/internal/lyrics"
)

type LyricsHandler struct {
	client *lyrics.Client
}

func NewLyricsHandler(client *lyrics.Client) *LyricsHandler {
	return &LyricsHandler{client: client}
}

type lyricsResponse struct {
	Title  string `json:"title"`
	Artist string `json:"artist"`
	Lyrics string `json:"lyrics"`
	URL    string `json:"url"`
}

// GetLyrics handles GET /lyrics?title=...&artist=...
func (h *LyricsHandler) GetLyrics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	title := r.URL.Query().Get("title")
	if title == "" {
		writeError(w, http.StatusBadRequest, "title parameter is required")
		return
	}

	artist := r.URL.Query().Get("artist")

	result, err := h.client.GetLyrics(r.Context(), title, artist)
	if err != nil {
		writeError(w, http.StatusBadGateway, "failed to fetch lyrics: "+err.Error())
		return
	}

	if result == nil {
		writeError(w, http.StatusNotFound, "no lyrics found")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(lyricsResponse{
		Title:  result.Title,
		Artist: result.Artist,
		Lyrics: result.Lyrics,
		URL:    result.URL,
	})
}
