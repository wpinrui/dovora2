package invidious

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
}

func NewClient(baseURL string) *Client {
	return &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

type SearchResult struct {
	Type            string           `json:"type"`
	VideoID         string           `json:"videoId"`
	Title           string           `json:"title"`
	Author          string           `json:"author"`
	AuthorID        string           `json:"authorId"`
	LengthSeconds   int              `json:"lengthSeconds"`
	ViewCount       int64            `json:"viewCount,omitempty"`
	Published       int64            `json:"published,omitempty"`
	PublishedText   string           `json:"publishedText,omitempty"`
	VideoThumbnails []VideoThumbnail `json:"videoThumbnails,omitempty"`
	Description     string           `json:"description,omitempty"`
	LiveNow         bool             `json:"liveNow,omitempty"`
}

type VideoThumbnail struct {
	Quality string `json:"quality"`
	URL     string `json:"url"`
	Width   int    `json:"width"`
	Height  int    `json:"height"`
}

func (c *Client) Search(ctx context.Context, query string, searchType string) ([]SearchResult, error) {
	if searchType == "" {
		searchType = "video"
	}

	endpoint := fmt.Sprintf("%s/api/v1/search?q=%s&type=%s",
		c.baseURL,
		url.QueryEscape(query),
		url.QueryEscape(searchType),
	)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("executing request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("invidious returned status %d", resp.StatusCode)
	}

	var results []SearchResult
	if err := json.NewDecoder(resp.Body).Decode(&results); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}

	// Filter to only video results (Invidious can return channels/playlists too)
	filtered := make([]SearchResult, 0, len(results))
	for _, r := range results {
		if r.Type == "video" {
			filtered = append(filtered, r)
		}
	}

	return filtered, nil
}
