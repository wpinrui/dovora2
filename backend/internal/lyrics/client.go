package lyrics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"golang.org/x/net/html"
)

type Client struct {
	apiKey     string
	httpClient *http.Client
}

func NewClient(apiKey string) *Client {
	return &Client{
		apiKey: apiKey,
		httpClient: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

type SearchResponse struct {
	Response struct {
		Hits []Hit `json:"hits"`
	} `json:"response"`
}

type Hit struct {
	Type   string `json:"type"`
	Result Song   `json:"result"`
}

type Song struct {
	ID                int    `json:"id"`
	Title             string `json:"title"`
	TitleWithFeatured string `json:"title_with_featured"`
	URL               string `json:"url"`
	Path              string `json:"path"`
	PrimaryArtist     Artist `json:"primary_artist"`
}

type Artist struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

type LyricsResult struct {
	Title  string `json:"title"`
	Artist string `json:"artist"`
	Lyrics string `json:"lyrics"`
	URL    string `json:"url"`
}

// GetLyrics searches for a song and returns its lyrics
func (c *Client) GetLyrics(ctx context.Context, title, artist string) (*LyricsResult, error) {
	// Build search query
	query := title
	if artist != "" {
		query = fmt.Sprintf("%s %s", artist, title)
	}

	// Search for the song
	song, err := c.searchSong(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("searching song: %w", err)
	}

	if song == nil {
		return nil, nil // No results found
	}

	// Scrape lyrics from the song page
	lyrics, err := c.scrapeLyrics(ctx, song.URL)
	if err != nil {
		return nil, fmt.Errorf("scraping lyrics: %w", err)
	}

	return &LyricsResult{
		Title:  song.Title,
		Artist: song.PrimaryArtist.Name,
		Lyrics: lyrics,
		URL:    song.URL,
	}, nil
}

func (c *Client) searchSong(ctx context.Context, query string) (*Song, error) {
	endpoint := fmt.Sprintf("https://api.genius.com/search?q=%s", url.QueryEscape(query))

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	req.Header.Set("Authorization", "Bearer "+c.apiKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("executing request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("genius API returned status %d: %s", resp.StatusCode, string(body))
	}

	var searchResp SearchResponse
	if err := json.NewDecoder(resp.Body).Decode(&searchResp); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}

	// Return first song result
	for _, hit := range searchResp.Response.Hits {
		if hit.Type == "song" {
			return &hit.Result, nil
		}
	}

	return nil, nil // No song found
}

func (c *Client) scrapeLyrics(ctx context.Context, songURL string) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, songURL, nil)
	if err != nil {
		return "", fmt.Errorf("creating request: %w", err)
	}

	// Set a user agent to avoid being blocked
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("executing request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("genius page returned status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("reading response body: %w", err)
	}

	return extractLyrics(string(body))
}

func extractLyrics(htmlContent string) (string, error) {
	doc, err := html.Parse(strings.NewReader(htmlContent))
	if err != nil {
		return "", fmt.Errorf("parsing HTML: %w", err)
	}

	var lyrics strings.Builder
	var extractText func(*html.Node)

	// Find lyrics containers - Genius uses data-lyrics-container="true"
	var findLyricsContainers func(*html.Node)
	findLyricsContainers = func(n *html.Node) {
		if n.Type == html.ElementNode {
			for _, attr := range n.Attr {
				if attr.Key == "data-lyrics-container" && attr.Val == "true" {
					extractText(n)
					lyrics.WriteString("\n")
				}
			}
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			findLyricsContainers(c)
		}
	}

	// Extract text content, preserving line breaks
	extractText = func(n *html.Node) {
		if n.Type == html.TextNode {
			text := strings.TrimSpace(n.Data)
			if text != "" {
				lyrics.WriteString(text)
			}
		} else if n.Type == html.ElementNode {
			// Handle line breaks
			if n.Data == "br" {
				lyrics.WriteString("\n")
			}
			for c := n.FirstChild; c != nil; c = c.NextSibling {
				extractText(c)
			}
		}
	}

	findLyricsContainers(doc)

	result := lyrics.String()
	if result == "" {
		return "", fmt.Errorf("no lyrics found on page")
	}

	// Clean up the result
	result = cleanLyrics(result)

	return result, nil
}

func cleanLyrics(lyrics string) string {
	// Remove excessive newlines
	re := regexp.MustCompile(`\n{3,}`)
	lyrics = re.ReplaceAllString(lyrics, "\n\n")

	// Trim whitespace
	lyrics = strings.TrimSpace(lyrics)

	return lyrics
}
