package db

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

var (
	ErrUserExists   = errors.New("user with this email already exists")
	ErrUserNotFound = errors.New("user not found")
)

type User struct {
	ID           string
	Email        string
	PasswordHash string
	IsAdmin      bool
	CreatedAt    time.Time
	UpdatedAt    time.Time
}

func (db *DB) GetUserByEmail(ctx context.Context, email string) (*User, error) {
	var user User
	err := db.Pool.QueryRow(ctx, `
		SELECT id, email, password_hash, is_admin, created_at, updated_at
		FROM users WHERE email = $1
	`, email).Scan(&user.ID, &user.Email, &user.PasswordHash, &user.IsAdmin, &user.CreatedAt, &user.UpdatedAt)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("get user by email: %w", err)
	}

	return &user, nil
}

// RegisterWithInvite creates a user and claims the invite atomically in a transaction.
// This prevents race conditions where two users could register with the same invite.
func (db *DB) RegisterWithInvite(ctx context.Context, email, passwordHash, inviteCode string) (*User, error) {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return nil, fmt.Errorf("begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	// Create user first
	var user User
	err = tx.QueryRow(ctx, `
		INSERT INTO users (email, password_hash)
		VALUES ($1, $2)
		RETURNING id, email, password_hash, is_admin, created_at, updated_at
	`, email, passwordHash).Scan(&user.ID, &user.Email, &user.PasswordHash, &user.IsAdmin, &user.CreatedAt, &user.UpdatedAt)

	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return nil, ErrUserExists
		}
		return nil, fmt.Errorf("create user: %w", err)
	}

	// Atomically claim the invite
	result, err := tx.Exec(ctx, `
		UPDATE invites
		SET used_by = $2, used_at = NOW()
		WHERE code = $1
		  AND used_by IS NULL
		  AND (expires_at IS NULL OR expires_at > NOW())
	`, inviteCode, user.ID)
	if err != nil {
		return nil, fmt.Errorf("claim invite: %w", err)
	}

	if result.RowsAffected() == 0 {
		// Invite claim failed - determine why for better error message
		var usedBy *string
		var expiresAt *time.Time
		err = tx.QueryRow(ctx, `
			SELECT used_by, expires_at FROM invites WHERE code = $1
		`, inviteCode).Scan(&usedBy, &expiresAt)
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return nil, ErrInviteNotFound
			}
			return nil, fmt.Errorf("check invite: %w", err)
		}
		if usedBy != nil {
			return nil, ErrInviteUsed
		}
		if expiresAt != nil && time.Now().After(*expiresAt) {
			return nil, ErrInviteExpired
		}
		return nil, fmt.Errorf("claim invite: unknown error")
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("commit transaction: %w", err)
	}

	return &user, nil
}

func (db *DB) GetUserByID(ctx context.Context, id string) (*User, error) {
	var user User
	err := db.Pool.QueryRow(ctx, `
		SELECT id, email, password_hash, is_admin, created_at, updated_at
		FROM users WHERE id = $1
	`, id).Scan(&user.ID, &user.Email, &user.PasswordHash, &user.IsAdmin, &user.CreatedAt, &user.UpdatedAt)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("get user by id: %w", err)
	}

	return &user, nil
}

func (db *DB) ListAllUsers(ctx context.Context) ([]User, error) {
	rows, err := db.Pool.Query(ctx, `
		SELECT id, email, password_hash, is_admin, created_at, updated_at
		FROM users
		ORDER BY created_at DESC
	`)
	if err != nil {
		return nil, fmt.Errorf("list users: %w", err)
	}
	defer rows.Close()

	var users []User
	for rows.Next() {
		var user User
		if err := rows.Scan(&user.ID, &user.Email, &user.PasswordHash, &user.IsAdmin, &user.CreatedAt, &user.UpdatedAt); err != nil {
			return nil, fmt.Errorf("scan user: %w", err)
		}
		users = append(users, user)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate users: %w", err)
	}

	return users, nil
}

func (db *DB) DeleteUser(ctx context.Context, id string) error {
	result, err := db.Pool.Exec(ctx, `DELETE FROM users WHERE id = $1`, id)
	if err != nil {
		return fmt.Errorf("delete user: %w", err)
	}
	if result.RowsAffected() == 0 {
		return ErrUserNotFound
	}
	return nil
}

func (db *DB) SetUserAdmin(ctx context.Context, id string, isAdmin bool) error {
	result, err := db.Pool.Exec(ctx, `
		UPDATE users SET is_admin = $2, updated_at = NOW() WHERE id = $1
	`, id, isAdmin)
	if err != nil {
		return fmt.Errorf("set user admin: %w", err)
	}
	if result.RowsAffected() == 0 {
		return ErrUserNotFound
	}
	return nil
}
