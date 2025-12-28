package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/wpinrui/dovora2/backend/internal/api"
	"github.com/wpinrui/dovora2/backend/internal/db"
	"github.com/wpinrui/dovora2/backend/internal/invidious"
	"github.com/wpinrui/dovora2/backend/internal/lyrics"
	"github.com/wpinrui/dovora2/backend/internal/ytdlp"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	databaseURL := os.Getenv("DATABASE_URL")
	if databaseURL == "" {
		log.Fatal("DATABASE_URL environment variable is required")
	}

	jwtSecret := os.Getenv("JWT_SECRET")
	if jwtSecret == "" {
		log.Fatal("JWT_SECRET environment variable is required")
	}

	invidiousURL := os.Getenv("INVIDIOUS_URL")
	if invidiousURL == "" {
		invidiousURL = "https://inv.perditum.com"
	}

	geniusAPIKey := os.Getenv("GENIUS_API_KEY")
	if geniusAPIKey == "" {
		log.Println("Warning: GENIUS_API_KEY not set, lyrics endpoint will not work")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	database, err := db.New(ctx, databaseURL)
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer database.Close()

	log.Println("Connected to database")

	// Run migrations
	if err := database.Migrate(ctx); err != nil {
		log.Fatalf("Failed to run migrations: %v", err)
	}
	log.Println("Migrations complete")

	invidiousClient := invidious.NewClient(invidiousURL)
	lyricsClient := lyrics.NewClient(geniusAPIKey)

	// Initialize yt-dlp downloader
	downloadsDir := os.Getenv("DOWNLOADS_DIR")
	if downloadsDir == "" {
		downloadsDir = "./downloads"
	}
	downloader, err := ytdlp.New(downloadsDir)
	if err != nil {
		log.Fatalf("Failed to initialize downloader: %v", err)
	}
	log.Printf("Downloads directory: %s", downloadsDir)

	authHandler := api.NewAuthHandler(database, jwtSecret)
	inviteHandler := api.NewInviteHandler(database)
	searchHandler := api.NewSearchHandler(invidiousClient)
	downloadHandler := api.NewDownloadHandler(database, downloader)
	fileHandler := api.NewFileHandler(database)
	libraryHandler := api.NewLibraryHandler(database)
	lyricsHandler := api.NewLyricsHandler(lyricsClient)
	playlistHandler := api.NewPlaylistHandler(database)
	middleware := api.NewMiddleware(jwtSecret)

	// Rate limiters: (requests per second, burst)
	authLimiter := api.NewRateLimiter(0.17, 5)     // ~10 req/min, burst of 5
	downloadLimiter := api.NewRateLimiter(0.08, 3) // ~5 req/min, burst of 3
	apiLimiter := api.NewRateLimiter(1.0, 10)      // 60 req/min, burst of 10

	http.HandleFunc("/health", healthHandler(database))
	http.HandleFunc("/auth/register", authLimiter.RateLimit(authHandler.Register))
	http.HandleFunc("/auth/login", authLimiter.RateLimit(authHandler.Login))
	http.HandleFunc("/auth/refresh", authLimiter.RateLimit(authHandler.Refresh))
	http.HandleFunc("/invites", apiLimiter.RateLimit(middleware.RequireAuth(inviteHandler.Create)))
	http.HandleFunc("/invites/list", apiLimiter.RateLimit(middleware.RequireAuth(inviteHandler.List)))
	http.HandleFunc("/search", apiLimiter.RateLimit(middleware.RequireAuth(searchHandler.Search)))
	http.HandleFunc("/download", middleware.RequireAuth(downloadLimiter.RateLimitByUser(downloadHandler.Download)))
	http.HandleFunc("/lyrics", apiLimiter.RateLimit(middleware.RequireAuth(lyricsHandler.GetLyrics)))
	http.HandleFunc("/files/", apiLimiter.RateLimit(middleware.RequireAuth(fileHandler.ServeFile)))
	http.HandleFunc("/library/music", apiLimiter.RateLimit(middleware.RequireAuth(libraryHandler.GetMusic)))
	http.HandleFunc("/library/videos", apiLimiter.RateLimit(middleware.RequireAuth(libraryHandler.GetVideos)))
	http.HandleFunc("/library/", apiLimiter.RateLimit(middleware.RequireAuth(libraryHandler.DeleteItem)))
	http.HandleFunc("/tracks/", apiLimiter.RateLimit(middleware.RequireAuth(libraryHandler.UpdateTrack)))
	http.HandleFunc("/playlists", apiLimiter.RateLimit(middleware.RequireAuth(playlistHandler.HandlePlaylists)))
	http.HandleFunc("/playlists/", apiLimiter.RateLimit(middleware.RequireAuth(playlistHandler.HandlePlaylist)))

	server := &http.Server{
		Addr:         ":" + port,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 10 * time.Minute, // Long timeout for downloads
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("Starting server on port %s", port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed to start: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}

	log.Println("Server stopped")
}

func healthHandler(database *db.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
		defer cancel()

		status := "ok"
		dbStatus := "ok"

		if err := database.Ping(ctx); err != nil {
			status = "degraded"
			dbStatus = "error"
		}

		response := map[string]string{
			"status":   status,
			"database": dbStatus,
		}

		w.Header().Set("Content-Type", "application/json")
		if status != "ok" {
			w.WriteHeader(http.StatusServiceUnavailable)
		}
		json.NewEncoder(w).Encode(response)
	}
}
