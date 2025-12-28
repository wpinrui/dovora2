package db

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
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

func (db *DB) GetInviteByCode(ctx context.Context, code string) (*Invite, error) {
	var invite Invite
	err := db.Pool.QueryRow(ctx, `
		SELECT id, code, created_by, used_by, created_at, used_at, expires_at
		FROM invites WHERE code = $1
	`, code).Scan(
		&invite.ID, &invite.Code, &invite.CreatedBy, &invite.UsedBy,
		&invite.CreatedAt, &invite.UsedAt, &invite.ExpiresAt,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrInviteNotFound
		}
		return nil, fmt.Errorf("get invite by code: %w", err)
	}

	return &invite, nil
}

func (db *DB) ValidateInvite(ctx context.Context, code string) (*Invite, error) {
	invite, err := db.GetInviteByCode(ctx, code)
	if err != nil {
		return nil, err
	}

	if invite.UsedBy != nil {
		return nil, ErrInviteUsed
	}

	if invite.ExpiresAt != nil && time.Now().After(*invite.ExpiresAt) {
		return nil, ErrInviteExpired
	}

	return invite, nil
}

func (db *DB) MarkInviteUsed(ctx context.Context, inviteID string, userID string) error {
	result, err := db.Pool.Exec(ctx, `
		UPDATE invites
		SET used_by = $1, used_at = NOW()
		WHERE id = $2 AND used_by IS NULL
	`, userID, inviteID)
	if err != nil {
		return fmt.Errorf("mark invite used: %w", err)
	}

	if result.RowsAffected() == 0 {
		return ErrInviteUsed
	}

	return nil
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
