package api

import (
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// RateLimiter manages per-client rate limiting
type RateLimiter struct {
	limiters sync.Map
	rate     rate.Limit
	burst    int
}

type limiterEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// NewRateLimiter creates a rate limiter with specified requests per second and burst size
func NewRateLimiter(rps float64, burst int) *RateLimiter {
	rl := &RateLimiter{
		rate:  rate.Limit(rps),
		burst: burst,
	}
	go rl.cleanupLoop()
	return rl
}

// getLimiter returns the rate limiter for a given key, creating one if needed
func (rl *RateLimiter) getLimiter(key string) *rate.Limiter {
	now := time.Now()

	// Fast path: entry already exists
	if v, ok := rl.limiters.Load(key); ok {
		entry := v.(*limiterEntry)
		entry.lastSeen = now
		return entry.limiter
	}

	// Slow path: create new entry, use LoadOrStore to handle race
	newEntry := &limiterEntry{
		limiter:  rate.NewLimiter(rl.rate, rl.burst),
		lastSeen: now,
	}

	v, loaded := rl.limiters.LoadOrStore(key, newEntry)
	entry := v.(*limiterEntry)

	if loaded {
		entry.lastSeen = now
	}

	return entry.limiter
}

// Allow checks if a request from the given key is allowed
func (rl *RateLimiter) Allow(key string) bool {
	return rl.getLimiter(key).Allow()
}

// cleanupLoop removes stale limiters every minute
func (rl *RateLimiter) cleanupLoop() {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()

	for range ticker.C {
		rl.cleanup()
	}
}

// cleanup removes limiters that haven't been used in 3 minutes
func (rl *RateLimiter) cleanup() {
	threshold := time.Now().Add(-3 * time.Minute)

	rl.limiters.Range(func(key, value interface{}) bool {
		entry := value.(*limiterEntry)
		if entry.lastSeen.Before(threshold) {
			rl.limiters.Delete(key)
		}
		return true
	})
}

// getClientIP extracts the client IP from the request
func getClientIP(r *http.Request) string {
	// Check X-Forwarded-For header (for reverse proxies)
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		// Take the first IP in the chain
		if idx := strings.Index(xff, ","); idx != -1 {
			return strings.TrimSpace(xff[:idx])
		}
		return strings.TrimSpace(xff)
	}

	// Check X-Real-IP header
	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		return strings.TrimSpace(xri)
	}

	// Fall back to RemoteAddr
	ip, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return ip
}

// RateLimit creates middleware that limits requests by client IP
func (rl *RateLimiter) RateLimit(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ip := getClientIP(r)

		if !rl.Allow(ip) {
			writeError(w, http.StatusTooManyRequests, "rate limit exceeded")
			return
		}

		next(w, r)
	}
}

// RateLimitByUser creates middleware that limits requests by user ID (for authenticated endpoints)
func (rl *RateLimiter) RateLimitByUser(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, ok := GetUserID(r.Context())
		if !ok {
			// Fall back to IP if user ID not available
			userID = getClientIP(r)
		}

		if !rl.Allow(userID) {
			writeError(w, http.StatusTooManyRequests, "rate limit exceeded")
			return
		}

		next(w, r)
	}
}
