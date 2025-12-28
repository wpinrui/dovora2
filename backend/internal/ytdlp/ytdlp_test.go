package ytdlp

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"
)

// mockRunner is a test implementation of CommandRunner
type mockRunner struct {
	output []byte
	err    error
	calls  []mockCall
}

type mockCall struct {
	name string
	args []string
}

func (m *mockRunner) Run(ctx context.Context, name string, args ...string) ([]byte, error) {
	m.calls = append(m.calls, mockCall{name: name, args: args})
	return m.output, m.err
}

func TestNew(t *testing.T) {
	t.Run("creates output directory", func(t *testing.T) {
		tmpDir := t.TempDir()
		outputDir := filepath.Join(tmpDir, "downloads")

		d, err := New(outputDir)
		if err != nil {
			t.Fatalf("New() error = %v", err)
		}

		if d == nil {
			t.Fatal("New() returned nil downloader")
		}

		// Verify directory was created
		info, err := os.Stat(outputDir)
		if err != nil {
			t.Fatalf("output directory not created: %v", err)
		}
		if !info.IsDir() {
			t.Error("output path is not a directory")
		}
	})

	t.Run("sets default paths", func(t *testing.T) {
		tmpDir := t.TempDir()

		d, err := New(tmpDir)
		if err != nil {
			t.Fatalf("New() error = %v", err)
		}

		if d.ytdlpPath != "yt-dlp" {
			t.Errorf("ytdlpPath = %v, want yt-dlp", d.ytdlpPath)
		}
		if d.ffmpegPath != "ffmpeg" {
			t.Errorf("ffmpegPath = %v, want ffmpeg", d.ffmpegPath)
		}
	})

	t.Run("applies WithYtdlpPath option", func(t *testing.T) {
		tmpDir := t.TempDir()

		d, err := New(tmpDir, WithYtdlpPath("/custom/yt-dlp"))
		if err != nil {
			t.Fatalf("New() error = %v", err)
		}

		if d.ytdlpPath != "/custom/yt-dlp" {
			t.Errorf("ytdlpPath = %v, want /custom/yt-dlp", d.ytdlpPath)
		}
	})

	t.Run("applies WithFfmpegPath option", func(t *testing.T) {
		tmpDir := t.TempDir()

		d, err := New(tmpDir, WithFfmpegPath("/custom/ffmpeg"))
		if err != nil {
			t.Fatalf("New() error = %v", err)
		}

		if d.ffmpegPath != "/custom/ffmpeg" {
			t.Errorf("ffmpegPath = %v, want /custom/ffmpeg", d.ffmpegPath)
		}
	})

	t.Run("applies WithCommandRunner option", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{}

		d, err := New(tmpDir, WithCommandRunner(runner))
		if err != nil {
			t.Fatalf("New() error = %v", err)
		}

		if d.runner != runner {
			t.Error("custom runner was not applied")
		}
	})

	t.Run("applies multiple options", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{}

		d, err := New(tmpDir,
			WithYtdlpPath("/custom/yt-dlp"),
			WithFfmpegPath("/custom/ffmpeg"),
			WithCommandRunner(runner),
		)
		if err != nil {
			t.Fatalf("New() error = %v", err)
		}

		if d.ytdlpPath != "/custom/yt-dlp" {
			t.Errorf("ytdlpPath = %v, want /custom/yt-dlp", d.ytdlpPath)
		}
		if d.ffmpegPath != "/custom/ffmpeg" {
			t.Errorf("ffmpegPath = %v, want /custom/ffmpeg", d.ffmpegPath)
		}
		if d.runner != runner {
			t.Error("custom runner was not applied")
		}
	})
}

func TestVideoURL(t *testing.T) {
	tests := []struct {
		videoID string
		want    string
	}{
		{"dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"},
		{"abc123", "https://www.youtube.com/watch?v=abc123"},
		{"", "https://www.youtube.com/watch?v="},
	}

	for _, tt := range tests {
		t.Run(tt.videoID, func(t *testing.T) {
			got := videoURL(tt.videoID)
			if got != tt.want {
				t.Errorf("videoURL(%q) = %v, want %v", tt.videoID, got, tt.want)
			}
		})
	}
}

func TestGetMetadata(t *testing.T) {
	t.Run("returns error for empty videoID", func(t *testing.T) {
		tmpDir := t.TempDir()
		d, _ := New(tmpDir)

		_, err := d.GetMetadata(context.Background(), "")
		if err == nil {
			t.Error("GetMetadata() should return error for empty videoID")
		}
		if err.Error() != "videoID is required" {
			t.Errorf("GetMetadata() error = %v, want 'videoID is required'", err)
		}
	})

	t.Run("parses metadata correctly", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{
			output: []byte(`{
				"id": "test123",
				"title": "Test Video",
				"artist": "Test Artist",
				"channel": "Test Channel",
				"duration": 180,
				"thumbnail": "https://example.com/thumb.jpg",
				"description": "Test description"
			}`),
		}

		d, _ := New(tmpDir, WithCommandRunner(runner))
		meta, err := d.GetMetadata(context.Background(), "test123")
		if err != nil {
			t.Fatalf("GetMetadata() error = %v", err)
		}

		if meta.ID != "test123" {
			t.Errorf("ID = %v, want test123", meta.ID)
		}
		if meta.Title != "Test Video" {
			t.Errorf("Title = %v, want Test Video", meta.Title)
		}
		if meta.Artist != "Test Artist" {
			t.Errorf("Artist = %v, want Test Artist", meta.Artist)
		}
		if meta.Channel != "Test Channel" {
			t.Errorf("Channel = %v, want Test Channel", meta.Channel)
		}
		if meta.Duration != 180 {
			t.Errorf("Duration = %v, want 180", meta.Duration)
		}
		if meta.Thumbnail != "https://example.com/thumb.jpg" {
			t.Errorf("Thumbnail = %v, want https://example.com/thumb.jpg", meta.Thumbnail)
		}
		if meta.Description != "Test description" {
			t.Errorf("Description = %v, want Test description", meta.Description)
		}
	})

	t.Run("uses uploader as channel fallback", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{
			output: []byte(`{
				"id": "test123",
				"title": "Test Video",
				"channel": "",
				"uploader": "Fallback Uploader",
				"duration": 60
			}`),
		}

		d, _ := New(tmpDir, WithCommandRunner(runner))
		meta, err := d.GetMetadata(context.Background(), "test123")
		if err != nil {
			t.Fatalf("GetMetadata() error = %v", err)
		}

		if meta.Channel != "Fallback Uploader" {
			t.Errorf("Channel = %v, want Fallback Uploader", meta.Channel)
		}
	})

	t.Run("returns error on command failure", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{
			err: errors.New("yt-dlp not found"),
		}

		d, _ := New(tmpDir, WithCommandRunner(runner))
		_, err := d.GetMetadata(context.Background(), "test123")
		if err == nil {
			t.Error("GetMetadata() should return error when command fails")
		}
	})

	t.Run("returns error on invalid JSON", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{
			output: []byte(`not valid json`),
		}

		d, _ := New(tmpDir, WithCommandRunner(runner))
		_, err := d.GetMetadata(context.Background(), "test123")
		if err == nil {
			t.Error("GetMetadata() should return error for invalid JSON")
		}
	})

	t.Run("passes correct arguments to yt-dlp", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{
			output: []byte(`{"id": "abc123", "title": "Test", "duration": 60}`),
		}

		d, _ := New(tmpDir, WithCommandRunner(runner))
		_, _ = d.GetMetadata(context.Background(), "abc123")

		if len(runner.calls) != 1 {
			t.Fatalf("expected 1 call, got %d", len(runner.calls))
		}

		call := runner.calls[0]
		if call.name != "yt-dlp" {
			t.Errorf("command = %v, want yt-dlp", call.name)
		}

		expectedArgs := []string{"--quiet", "--dump-json", "--no-download", "https://www.youtube.com/watch?v=abc123"}
		if len(call.args) != len(expectedArgs) {
			t.Fatalf("args length = %d, want %d", len(call.args), len(expectedArgs))
		}
		for i, arg := range expectedArgs {
			if call.args[i] != arg {
				t.Errorf("args[%d] = %v, want %v", i, call.args[i], arg)
			}
		}
	})
}

func TestDownloadAudio(t *testing.T) {
	t.Run("returns error for empty videoID", func(t *testing.T) {
		tmpDir := t.TempDir()
		d, _ := New(tmpDir)

		_, err := d.DownloadAudio(context.Background(), "")
		if err == nil {
			t.Error("DownloadAudio() should return error for empty videoID")
		}
		if err.Error() != "videoID is required" {
			t.Errorf("DownloadAudio() error = %v, want 'videoID is required'", err)
		}
	})

	t.Run("creates audio subdirectory", func(t *testing.T) {
		tmpDir := t.TempDir()

		// Create a file at the expected path so the download "succeeds"
		audioDir := filepath.Join(tmpDir, "audio")
		_ = os.MkdirAll(audioDir, 0755)
		testFile := filepath.Join(audioDir, "test123.m4a")
		_ = os.WriteFile(testFile, []byte("fake audio"), 0644)

		d, _ := New(tmpDir, WithCommandRunner(&sequentialMockRunner{
			responses: []mockResponse{
				{output: []byte(testFile + "\n")},
				{output: []byte(`{"id": "test123", "title": "Test", "duration": 60}`)},
			},
		}))

		result, err := d.DownloadAudio(context.Background(), "test123")
		if err != nil {
			t.Fatalf("DownloadAudio() error = %v", err)
		}

		if result.MediaType != MediaTypeAudio {
			t.Errorf("MediaType = %v, want audio", result.MediaType)
		}

		// Verify subdirectory was created
		info, err := os.Stat(audioDir)
		if err != nil {
			t.Fatalf("audio directory not created: %v", err)
		}
		if !info.IsDir() {
			t.Error("audio path is not a directory")
		}
	})

	t.Run("returns error when command fails", func(t *testing.T) {
		tmpDir := t.TempDir()
		runner := &mockRunner{
			err: errors.New("download failed"),
		}

		d, _ := New(tmpDir, WithCommandRunner(runner))
		_, err := d.DownloadAudio(context.Background(), "test123")
		if err == nil {
			t.Error("DownloadAudio() should return error when command fails")
		}
	})
}

func TestDownloadVideo(t *testing.T) {
	t.Run("returns error for empty videoID", func(t *testing.T) {
		tmpDir := t.TempDir()
		d, _ := New(tmpDir)

		_, err := d.DownloadVideo(context.Background(), "")
		if err == nil {
			t.Error("DownloadVideo() should return error for empty videoID")
		}
	})

	t.Run("creates video subdirectory", func(t *testing.T) {
		tmpDir := t.TempDir()

		// Create a file at the expected path so the download "succeeds"
		videoDir := filepath.Join(tmpDir, "video")
		_ = os.MkdirAll(videoDir, 0755)
		testFile := filepath.Join(videoDir, "test123.mp4")
		_ = os.WriteFile(testFile, []byte("fake video"), 0644)

		d, _ := New(tmpDir, WithCommandRunner(&sequentialMockRunner{
			responses: []mockResponse{
				{output: []byte(testFile + "\n")},
				{output: []byte(`{"id": "test123", "title": "Test", "duration": 60}`)},
			},
		}))

		result, err := d.DownloadVideo(context.Background(), "test123")
		if err != nil {
			t.Fatalf("DownloadVideo() error = %v", err)
		}

		if result.MediaType != MediaTypeVideo {
			t.Errorf("MediaType = %v, want video", result.MediaType)
		}
	})

	t.Run("adds ffmpeg location when custom path set", func(t *testing.T) {
		tmpDir := t.TempDir()

		// Create test file
		videoDir := filepath.Join(tmpDir, "video")
		_ = os.MkdirAll(videoDir, 0755)
		testFile := filepath.Join(videoDir, "test123.mp4")
		_ = os.WriteFile(testFile, []byte("fake video"), 0644)

		seqRunner := &sequentialMockRunner{
			responses: []mockResponse{
				{output: []byte(testFile + "\n")},
				{output: []byte(`{"id": "test123", "title": "Test", "duration": 60}`)},
			},
		}

		d, _ := New(tmpDir,
			WithFfmpegPath("/custom/ffmpeg"),
			WithCommandRunner(seqRunner),
		)

		_, _ = d.DownloadVideo(context.Background(), "test123")

		// First call should have ffmpeg-location flag
		if len(seqRunner.calls) < 1 {
			t.Fatal("expected at least 1 call")
		}

		call := seqRunner.calls[0]
		foundFfmpegFlag := false
		for i, arg := range call.args {
			if arg == "--ffmpeg-location" && i+1 < len(call.args) && call.args[i+1] == "/custom/ffmpeg" {
				foundFfmpegFlag = true
				break
			}
		}
		if !foundFfmpegFlag {
			t.Error("--ffmpeg-location flag not found in arguments")
		}
	})
}

func TestDownloadResult(t *testing.T) {
	t.Run("contains minimal metadata on metadata fetch failure", func(t *testing.T) {
		tmpDir := t.TempDir()

		// Create test file
		audioDir := filepath.Join(tmpDir, "audio")
		_ = os.MkdirAll(audioDir, 0755)
		testFile := filepath.Join(audioDir, "test123.m4a")
		_ = os.WriteFile(testFile, []byte("fake audio"), 0644)

		// First call succeeds (download), second call fails (metadata)
		seqRunner := &sequentialMockRunner{
			responses: []mockResponse{
				{output: []byte(testFile + "\n")},
				{err: errors.New("metadata fetch failed")},
			},
		}

		d, _ := New(tmpDir, WithCommandRunner(seqRunner))
		result, err := d.DownloadAudio(context.Background(), "test123")
		if err != nil {
			t.Fatalf("DownloadAudio() error = %v", err)
		}

		// Should still return result with minimal metadata
		if result.Metadata.ID != "test123" {
			t.Errorf("Metadata.ID = %v, want test123", result.Metadata.ID)
		}
		if result.FilePath != testFile {
			t.Errorf("FilePath = %v, want %v", result.FilePath, testFile)
		}
	})

	t.Run("uses fallback path when output is empty", func(t *testing.T) {
		tmpDir := t.TempDir()

		// Create test file at fallback location
		audioDir := filepath.Join(tmpDir, "audio")
		_ = os.MkdirAll(audioDir, 0755)
		fallbackFile := filepath.Join(audioDir, "test123.m4a")
		_ = os.WriteFile(fallbackFile, []byte("fake audio"), 0644)

		// Return empty output to trigger fallback
		seqRunner := &sequentialMockRunner{
			responses: []mockResponse{
				{output: []byte("")},
				{output: []byte(`{"id": "test123", "title": "Test", "duration": 60}`)},
			},
		}

		d, _ := New(tmpDir, WithCommandRunner(seqRunner))
		result, err := d.DownloadAudio(context.Background(), "test123")
		if err != nil {
			t.Fatalf("DownloadAudio() error = %v", err)
		}

		if result.FilePath != fallbackFile {
			t.Errorf("FilePath = %v, want %v", result.FilePath, fallbackFile)
		}
	})
}

// sequentialMockRunner returns different responses for each call
type sequentialMockRunner struct {
	responses []mockResponse
	calls     []mockCall
	callIndex int
}

type mockResponse struct {
	output []byte
	err    error
}

func (m *sequentialMockRunner) Run(ctx context.Context, name string, args ...string) ([]byte, error) {
	m.calls = append(m.calls, mockCall{name: name, args: args})

	if m.callIndex >= len(m.responses) {
		return nil, errors.New("no more mock responses")
	}

	resp := m.responses[m.callIndex]
	m.callIndex++
	return resp.output, resp.err
}
