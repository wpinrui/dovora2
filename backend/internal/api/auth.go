package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"net/mail"
	"unicode"

	"github.com/wpinrui/dovora2/backend/internal/auth"
	"github.com/wpinrui/dovora2/backend/internal/db"
)

type AuthHandler struct {
	db *db.DB
}

func NewAuthHandler(database *db.DB) *AuthHandler {
	return &AuthHandler{db: database}
}

type registerRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type registerResponse struct {
	ID    string `json:"id"`
	Email string `json:"email"`
}

type errorResponse struct {
	Error string `json:"error"`
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if err := validateEmail(req.Email); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	if err := validatePassword(req.Password); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	passwordHash, err := auth.HashPassword(req.Password)
	if err != nil {
		log.Printf("Failed to hash password: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	user, err := h.db.CreateUser(r.Context(), req.Email, passwordHash)
	if err != nil {
		if errors.Is(err, db.ErrUserExists) {
			writeError(w, http.StatusConflict, "email already registered")
			return
		}
		log.Printf("Failed to create user: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(registerResponse{
		ID:    user.ID,
		Email: user.Email,
	})
}

func validateEmail(email string) error {
	if email == "" {
		return errors.New("email is required")
	}
	if _, err := mail.ParseAddress(email); err != nil {
		return errors.New("invalid email format")
	}
	return nil
}

func validatePassword(password string) error {
	if password == "" {
		return errors.New("password is required")
	}
	if len(password) < 8 {
		return errors.New("password must be at least 8 characters")
	}
	if len(password) > 72 {
		return errors.New("password must be at most 72 characters")
	}

	var hasUpper, hasLower, hasDigit bool
	for _, r := range password {
		switch {
		case unicode.IsUpper(r):
			hasUpper = true
		case unicode.IsLower(r):
			hasLower = true
		case unicode.IsDigit(r):
			hasDigit = true
		}
	}

	if !hasUpper {
		return errors.New("password must contain at least one uppercase letter")
	}
	if !hasLower {
		return errors.New("password must contain at least one lowercase letter")
	}
	if !hasDigit {
		return errors.New("password must contain at least one digit")
	}

	return nil
}

func writeError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(errorResponse{Error: message})
}
