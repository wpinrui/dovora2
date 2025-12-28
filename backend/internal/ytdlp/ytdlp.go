package ytdlp

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// MediaType represents the type of media to download
type MediaType string

const (
	MediaTypeAudio MediaType = "audio"
	MediaTypeVideo MediaType = "video"

	youtubeURLFormat = "https://www.youtube.com/watch?v=%s"
)

// Metadata contains information about a video/audio
type Metadata struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Artist      string `json:"artist,omitempty"`
	Channel     string `json:"channel"`
	Duration    int    `json:"duration"`
	Thumbnail   string `json:"thumbnail"`
	Description string `json:"description,omitempty"`
}

// DownloadResult contains information about a completed download
type DownloadResult struct {
	FilePath  string
	Metadata  Metadata
	MediaType MediaType
}

// Downloader wraps yt-dlp for downloading media
type Downloader struct {
	outputDir  string
	ytdlpPath  string
	ffmpegPath string
}

// Option configures the Downloader
type Option func(*Downloader)

// WithYtdlpPath sets a custom path to the yt-dlp executable
func WithYtdlpPath(path string) Option {
	return func(d *Downloader) {
		d.ytdlpPath = path
	}
}

// WithFfmpegPath sets a custom path to the ffmpeg executable
func WithFfmpegPath(path string) Option {
	return func(d *Downloader) {
		d.ffmpegPath = path
	}
}

// videoURL returns the YouTube URL for a video ID
func videoURL(videoID string) string {
	return fmt.Sprintf(youtubeURLFormat, videoID)
}

// runYtdlp executes yt-dlp with the given arguments and returns the output
func (d *Downloader) runYtdlp(ctx context.Context, args ...string) ([]byte, error) {
	cmd := exec.CommandContext(ctx, d.ytdlpPath, args...)
	output, err := cmd.Output()
	if err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return nil, fmt.Errorf("yt-dlp failed: %s", string(exitErr.Stderr))
		}
		return nil, fmt.Errorf("executing yt-dlp: %w", err)
	}
	return output, nil
}

// New creates a new Downloader
func New(outputDir string, opts ...Option) (*Downloader, error) {
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return nil, fmt.Errorf("creating output directory: %w", err)
	}

	d := &Downloader{
		outputDir:  outputDir,
		ytdlpPath:  "yt-dlp",
		ffmpegPath: "ffmpeg",
	}

	for _, opt := range opts {
		opt(d)
	}

	return d, nil
}

// GetMetadata fetches metadata for a video without downloading it
func (d *Downloader) GetMetadata(ctx context.Context, videoID string) (*Metadata, error) {
	url := videoURL(videoID)

	output, err := d.runYtdlp(ctx, "--quiet", "--dump-json", "--no-download", url)
	if err != nil {
		return nil, err
	}

	var raw struct {
		ID          string `json:"id"`
		Title       string `json:"title"`
		Artist      string `json:"artist"`
		Channel     string `json:"channel"`
		Uploader    string `json:"uploader"`
		Duration    int    `json:"duration"`
		Thumbnail   string `json:"thumbnail"`
		Description string `json:"description"`
	}

	if err := json.Unmarshal(output, &raw); err != nil {
		return nil, fmt.Errorf("parsing metadata: %w", err)
	}

	// Use uploader as fallback for channel
	channel := raw.Channel
	if channel == "" {
		channel = raw.Uploader
	}

	return &Metadata{
		ID:          raw.ID,
		Title:       raw.Title,
		Artist:      raw.Artist,
		Channel:     channel,
		Duration:    raw.Duration,
		Thumbnail:   raw.Thumbnail,
		Description: raw.Description,
	}, nil
}

// DownloadAudio downloads audio in M4A format
func (d *Downloader) DownloadAudio(ctx context.Context, videoID string) (*DownloadResult, error) {
	return d.download(ctx, videoID, MediaTypeAudio)
}

// DownloadVideo downloads video in the best available quality
func (d *Downloader) DownloadVideo(ctx context.Context, videoID string) (*DownloadResult, error) {
	return d.download(ctx, videoID, MediaTypeVideo)
}

func (d *Downloader) download(ctx context.Context, videoID string, mediaType MediaType) (*DownloadResult, error) {
	url := videoURL(videoID)

	// Create subdirectory based on media type
	subDir := filepath.Join(d.outputDir, string(mediaType))
	if err := os.MkdirAll(subDir, 0755); err != nil {
		return nil, fmt.Errorf("creating subdirectory: %w", err)
	}

	// Output template: videoID.ext
	outputTemplate := filepath.Join(subDir, "%(id)s.%(ext)s")

	var args []string
	var expectedExt string

	switch mediaType {
	case MediaTypeAudio:
		args = []string{
			"--quiet",
			"-x",
			"--audio-format", "m4a",
			"--audio-quality", "0",
			"-o", outputTemplate,
			"--print", "after_move:filepath",
			"--no-playlist",
			url,
		}
		expectedExt = "m4a"
	case MediaTypeVideo:
		args = []string{
			"--quiet",
			"-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
			"--merge-output-format", "mp4",
			"-o", outputTemplate,
			"--print", "after_move:filepath",
			"--no-playlist",
			url,
		}
		expectedExt = "mp4"
	default:
		return nil, fmt.Errorf("unsupported media type: %s", mediaType)
	}

	// Add ffmpeg path if custom
	if d.ffmpegPath != "ffmpeg" {
		args = append([]string{"--ffmpeg-location", d.ffmpegPath}, args...)
	}

	output, err := d.runYtdlp(ctx, args...)
	if err != nil {
		return nil, err
	}

	// Parse the output file path
	filePath := strings.TrimSpace(string(output))
	if filePath == "" {
		// Fallback: construct expected path
		filePath = filepath.Join(subDir, videoID+"."+expectedExt)
	}

	// Verify file exists
	if _, err := os.Stat(filePath); err != nil {
		return nil, fmt.Errorf("verifying downloaded file: %w", err)
	}

	// Fetch metadata
	metadata, err := d.GetMetadata(ctx, videoID)
	if err != nil {
		// Non-fatal: return result with minimal metadata
		metadata = &Metadata{
			ID: videoID,
		}
	}

	return &DownloadResult{
		FilePath:  filePath,
		Metadata:  *metadata,
		MediaType: mediaType,
	}, nil
}
