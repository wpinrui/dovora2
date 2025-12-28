package api

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/wpinrui/dovora2/backend/internal/db"
)

type InviteHandler struct {
	db *db.DB
}

func NewInviteHandler(database *db.DB) *InviteHandler {
	return &InviteHandler{db: database}
}

type createInviteResponse struct {
	ID   string `json:"id"`
	Code string `json:"code"`
}

type inviteResponse struct {
	ID        string  `json:"id"`
	Code      string  `json:"code"`
	Used      bool    `json:"used"`
	CreatedAt string  `json:"created_at"`
	UsedAt    *string `json:"used_at,omitempty"`
}

func (h *InviteHandler) Create(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	invite, err := h.db.CreateInvite(r.Context(), &userID, nil)
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

func (h *InviteHandler) List(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	userID, ok := GetUserID(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	invites, err := h.db.ListInvitesByCreator(r.Context(), userID)
	if err != nil {
		log.Printf("Failed to list invites: %v", err)
		writeError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	response := make([]inviteResponse, len(invites))
	for i, inv := range invites {
		resp := inviteResponse{
			ID:        inv.ID,
			Code:      inv.Code,
			Used:      inv.UsedBy != nil,
			CreatedAt: inv.CreatedAt.Format("2006-01-02T15:04:05Z07:00"),
		}
		if inv.UsedAt != nil {
			usedAt := inv.UsedAt.Format("2006-01-02T15:04:05Z07:00")
			resp.UsedAt = &usedAt
		}
		response[i] = resp
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}
