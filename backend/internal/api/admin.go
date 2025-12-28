package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"

	"github.com/wpinrui/dovora2/backend/internal/db"
)

type AdminHandler struct {
	db *db.DB
}

func NewAdminHandler(database *db.DB) *AdminHandler {
	return &AdminHandler{db: database}
}

type userResponse struct {
	ID        string `json:"id"`
	Email     string `json:"email"`
	IsAdmin   bool   `json:"is_admin"`
	CreatedAt string `json:"created_at"`
}

type adminInviteResponse struct {
	ID        string  `json:"id"`
	Code      string  `json:"code"`
	CreatedBy *string `json:"created_by,omitempty"`
	UsedBy    *string `json:"used_by,omitempty"`
	CreatedAt string  `json:"created_at"`
	UsedAt    *string `json:"used_at,omitempty"`
	ExpiresAt *string `json:"expires_at,omitempty"`
}

type setAdminRequest struct {
	IsAdmin bool `json:"is_admin"`
}

// HandleUsers routes requests for /admin/users and /admin/users/{id}[/admin]
func (h *AdminHandler) HandleUsers(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/admin/users")
	path = strings.TrimPrefix(path, "/")

	switch {
	case path == "" && r.Method == http.MethodGet:
		h.listUsers(w, r)
	case path != "" && strings.HasSuffix(path, "/admin") && r.Method == http.MethodPut:
		h.setUserAdmin(w, r, strings.TrimSuffix(path, "/admin"))
	case path != "" && r.Method == http.MethodDelete:
		h.deleteUser(w, r, path)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// HandleInvites routes requests for /admin/invites and /admin/invites/{id}
func (h *AdminHandler) HandleInvites(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/admin/invites")
	path = strings.TrimPrefix(path, "/")

	switch {
	case path == "" && r.Method == http.MethodGet:
		h.listInvites(w, r)
	case path == "" && r.Method == http.MethodPost:
		h.createInvite(w, r)
	case path != "" && r.Method == http.MethodDelete:
		h.deleteInvite(w, r, path)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (h *AdminHandler) listUsers(w http.ResponseWriter, r *http.Request) {
	users, err := h.db.ListAllUsers(r.Context())
	if err != nil {
		log.Printf("Failed to list users: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	response := make([]userResponse, len(users))
	for i, u := range users {
		response[i] = userResponse{
			ID:        u.ID,
			Email:     u.Email,
			IsAdmin:   u.IsAdmin,
			CreatedAt: u.CreatedAt.Format(timeFormat),
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *AdminHandler) deleteUser(w http.ResponseWriter, r *http.Request, userID string) {
	// Prevent self-deletion
	currentUserID, _ := GetUserID(r.Context())
	if userID == currentUserID {
		writeError(w, http.StatusBadRequest, "cannot delete yourself")
		return
	}

	err := h.db.DeleteUser(r.Context(), userID)
	if err != nil {
		if errors.Is(err, db.ErrUserNotFound) {
			writeError(w, http.StatusNotFound, "user not found")
			return
		}
		log.Printf("Failed to delete user: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *AdminHandler) setUserAdmin(w http.ResponseWriter, r *http.Request, userID string) {
	var req setAdminRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	// Prevent removing own admin status
	currentUserID, _ := GetUserID(r.Context())
	if userID == currentUserID && !req.IsAdmin {
		writeError(w, http.StatusBadRequest, "cannot remove your own admin status")
		return
	}

	err := h.db.SetUserAdmin(r.Context(), userID, req.IsAdmin)
	if err != nil {
		if errors.Is(err, db.ErrUserNotFound) {
			writeError(w, http.StatusNotFound, "user not found")
			return
		}
		log.Printf("Failed to set user admin: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *AdminHandler) listInvites(w http.ResponseWriter, r *http.Request) {
	invites, err := h.db.ListAllInvites(r.Context())
	if err != nil {
		log.Printf("Failed to list invites: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	response := make([]adminInviteResponse, len(invites))
	for i, inv := range invites {
		resp := adminInviteResponse{
			ID:        inv.ID,
			Code:      inv.Code,
			CreatedBy: inv.CreatedBy,
			UsedBy:    inv.UsedBy,
			CreatedAt: inv.CreatedAt.Format(timeFormat),
		}
		if inv.UsedAt != nil {
			usedAt := inv.UsedAt.Format(timeFormat)
			resp.UsedAt = &usedAt
		}
		if inv.ExpiresAt != nil {
			expiresAt := inv.ExpiresAt.Format(timeFormat)
			resp.ExpiresAt = &expiresAt
		}
		response[i] = resp
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (h *AdminHandler) createInvite(w http.ResponseWriter, r *http.Request) {
	// Admin-created invites have no creator (created_by = null)
	invite, err := h.db.CreateInvite(r.Context(), nil, nil)
	if err != nil {
		log.Printf("Failed to create invite: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(createInviteResponse{
		ID:   invite.ID,
		Code: invite.Code,
	})
}

func (h *AdminHandler) deleteInvite(w http.ResponseWriter, r *http.Request, inviteID string) {
	err := h.db.DeleteInvite(r.Context(), inviteID)
	if err != nil {
		if errors.Is(err, db.ErrInviteNotFound) {
			writeError(w, http.StatusNotFound, "invite not found")
			return
		}
		log.Printf("Failed to delete invite: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
