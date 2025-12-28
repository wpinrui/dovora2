package auth

import (
	"strings"
	"testing"
)

func TestHashPassword(t *testing.T) {
	password := "testPassword123!"

	hash, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	if hash == "" {
		t.Error("HashPassword() returned empty hash")
	}

	if hash == password {
		t.Error("HashPassword() returned unhashed password")
	}

	// bcrypt hashes start with $2a$ or $2b$
	if !strings.HasPrefix(hash, "$2") {
		t.Errorf("HashPassword() hash doesn't look like bcrypt: %s", hash)
	}
}

func TestHashPassword_DifferentHashesForSamePassword(t *testing.T) {
	password := "testPassword123!"

	hash1, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword() first call error = %v", err)
	}

	hash2, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword() second call error = %v", err)
	}

	if hash1 == hash2 {
		t.Error("HashPassword() should generate different hashes for same password (due to salt)")
	}
}

func TestCheckPassword_CorrectPassword(t *testing.T) {
	password := "testPassword123!"

	hash, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	if !CheckPassword(password, hash) {
		t.Error("CheckPassword() returned false for correct password")
	}
}

func TestCheckPassword_WrongPassword(t *testing.T) {
	password := "testPassword123!"
	wrongPassword := "wrongPassword456!"

	hash, err := HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	if CheckPassword(wrongPassword, hash) {
		t.Error("CheckPassword() returned true for wrong password")
	}
}

func TestCheckPassword_InvalidHash(t *testing.T) {
	if CheckPassword("anyPassword", "invalidhash") {
		t.Error("CheckPassword() returned true for invalid hash")
	}
}

func TestCheckPassword_EmptyPassword(t *testing.T) {
	hash, err := HashPassword("realPassword")
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	if CheckPassword("", hash) {
		t.Error("CheckPassword() returned true for empty password")
	}
}
