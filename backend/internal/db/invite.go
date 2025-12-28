package db

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"time"
)

var (
	ErrInviteNotFound = errors.New("invite not found")
	ErrInviteUsed     = errors.New("invite already used")
	ErrInviteExpired  = errors.New("invite expired")
)

type Invite struct {
	ID        string
	Code      string
	CreatedBy *string
	UsedBy    *string
	CreatedAt time.Time
	UsedAt    *time.Time
	ExpiresAt *time.Time
}

func GenerateInviteCode() (string, error) {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("generate random bytes: %w", err)
	}
	return hex.EncodeToString(bytes), nil
}

func (db *DB) CreateInvite(ctx context.Context, createdBy *string, expiresAt *time.Time) (*Invite, error) {
	code, err := GenerateInviteCode()
	if err != nil {
		return nil, err
	}

	var invite Invite
	err = db.Pool.QueryRow(ctx, `
		INSERT INTO invites (code, created_by, expires_at)
		VALUES ($1, $2, $3)
		RETURNING id, code, created_by, used_by, created_at, used_at, expires_at
	`, code, createdBy, expiresAt).Scan(
		&invite.ID, &invite.Code, &invite.CreatedBy, &invite.UsedBy,
		&invite.CreatedAt, &invite.UsedAt, &invite.ExpiresAt,
	)
	if err != nil {
		return nil, fmt.Errorf("create invite: %w", err)
	}

	return &invite, nil
}

func (db *DB) ListInvitesByCreator(ctx context.Context, creatorID string) ([]Invite, error) {
	rows, err := db.Pool.Query(ctx, `
		SELECT id, code, created_by, used_by, created_at, used_at, expires_at
		FROM invites WHERE created_by = $1
		ORDER BY created_at DESC
	`, creatorID)
	if err != nil {
		return nil, fmt.Errorf("list invites by creator: %w", err)
	}
	defer rows.Close()

	var invites []Invite
	for rows.Next() {
		var invite Invite
		if err := rows.Scan(
			&invite.ID, &invite.Code, &invite.CreatedBy, &invite.UsedBy,
			&invite.CreatedAt, &invite.UsedAt, &invite.ExpiresAt,
		); err != nil {
			return nil, fmt.Errorf("scan invite: %w", err)
		}
		invites = append(invites, invite)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate invites: %w", err)
	}

	return invites, nil
}

func (db *DB) ListAllInvites(ctx context.Context) ([]Invite, error) {
	rows, err := db.Pool.Query(ctx, `
		SELECT id, code, created_by, used_by, created_at, used_at, expires_at
		FROM invites
		ORDER BY created_at DESC
	`)
	if err != nil {
		return nil, fmt.Errorf("list all invites: %w", err)
	}
	defer rows.Close()

	var invites []Invite
	for rows.Next() {
		var invite Invite
		if err := rows.Scan(
			&invite.ID, &invite.Code, &invite.CreatedBy, &invite.UsedBy,
			&invite.CreatedAt, &invite.UsedAt, &invite.ExpiresAt,
		); err != nil {
			return nil, fmt.Errorf("scan invite: %w", err)
		}
		invites = append(invites, invite)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate invites: %w", err)
	}

	return invites, nil
}

func (db *DB) DeleteInvite(ctx context.Context, id string) error {
	result, err := db.Pool.Exec(ctx, `DELETE FROM invites WHERE id = $1`, id)
	if err != nil {
		return fmt.Errorf("delete invite: %w", err)
	}
	if result.RowsAffected() == 0 {
		return ErrInviteNotFound
	}
	return nil
}
