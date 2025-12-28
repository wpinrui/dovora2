package api

import (
	"context"
	"net/http"
	"strings"

	"github.com/wpinrui/dovora2/backend/internal/auth"
	"github.com/wpinrui/dovora2/backend/internal/db"
)

type contextKey string

const UserIDKey contextKey = "userID"

type Middleware struct {
	jwtSecret string
	db        *db.DB
}

func NewMiddleware(jwtSecret string, database *db.DB) *Middleware {
	return &Middleware{jwtSecret: jwtSecret, db: database}
}

func (m *Middleware) RequireAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			writeError(w, http.StatusUnauthorized, "missing authorization header")
			return
		}

		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || strings.ToLower(parts[0]) != "bearer" {
			writeError(w, http.StatusUnauthorized, "invalid authorization header format")
			return
		}

		tokenString := parts[1]
		claims, err := auth.ValidateToken(tokenString, m.jwtSecret, auth.TokenTypeAccess)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid or expired token")
			return
		}

		ctx := context.WithValue(r.Context(), UserIDKey, claims.UserID)
		next(w, r.WithContext(ctx))
	}
}

func GetUserID(ctx context.Context) (string, bool) {
	userID, ok := ctx.Value(UserIDKey).(string)
	return userID, ok
}

func (m *Middleware) RequireAdmin(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, ok := GetUserID(r.Context())
		if !ok {
			writeError(w, http.StatusUnauthorized, "unauthorized")
			return
		}

		user, err := m.db.GetUserByID(r.Context(), userID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "failed to get user")
			return
		}
		if user == nil {
			writeError(w, http.StatusUnauthorized, "user not found")
			return
		}
		if !user.IsAdmin {
			writeError(w, http.StatusForbidden, "admin access required")
			return
		}

		next(w, r)
	}
}
