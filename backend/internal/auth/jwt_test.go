package auth

import (
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const testSecret = "test-secret-key-for-testing"

func TestGenerateTokenPair(t *testing.T) {
	userID := "user-123"

	pair, err := GenerateTokenPair(userID, testSecret)
	if err != nil {
		t.Fatalf("GenerateTokenPair() error = %v", err)
	}

	if pair.AccessToken == "" {
		t.Error("GenerateTokenPair() returned empty access token")
	}

	if pair.RefreshToken == "" {
		t.Error("GenerateTokenPair() returned empty refresh token")
	}

	if pair.AccessToken == pair.RefreshToken {
		t.Error("GenerateTokenPair() access and refresh tokens should be different")
	}
}

func TestValidateToken_ValidAccessToken(t *testing.T) {
	userID := "user-123"

	pair, err := GenerateTokenPair(userID, testSecret)
	if err != nil {
		t.Fatalf("GenerateTokenPair() error = %v", err)
	}

	claims, err := ValidateToken(pair.AccessToken, testSecret, TokenTypeAccess)
	if err != nil {
		t.Fatalf("ValidateToken() error = %v", err)
	}

	if claims.UserID != userID {
		t.Errorf("ValidateToken() UserID = %v, want %v", claims.UserID, userID)
	}

	if claims.TokenType != TokenTypeAccess {
		t.Errorf("ValidateToken() TokenType = %v, want %v", claims.TokenType, TokenTypeAccess)
	}
}

func TestValidateToken_ValidRefreshToken(t *testing.T) {
	userID := "user-456"

	pair, err := GenerateTokenPair(userID, testSecret)
	if err != nil {
		t.Fatalf("GenerateTokenPair() error = %v", err)
	}

	claims, err := ValidateToken(pair.RefreshToken, testSecret, TokenTypeRefresh)
	if err != nil {
		t.Fatalf("ValidateToken() error = %v", err)
	}

	if claims.UserID != userID {
		t.Errorf("ValidateToken() UserID = %v, want %v", claims.UserID, userID)
	}

	if claims.TokenType != TokenTypeRefresh {
		t.Errorf("ValidateToken() TokenType = %v, want %v", claims.TokenType, TokenTypeRefresh)
	}
}

func TestValidateToken_WrongTokenType(t *testing.T) {
	userID := "user-123"

	pair, err := GenerateTokenPair(userID, testSecret)
	if err != nil {
		t.Fatalf("GenerateTokenPair() error = %v", err)
	}

	// Try to validate access token as refresh token
	_, err = ValidateToken(pair.AccessToken, testSecret, TokenTypeRefresh)
	if err == nil {
		t.Error("ValidateToken() should fail when token type doesn't match")
	}

	// Try to validate refresh token as access token
	_, err = ValidateToken(pair.RefreshToken, testSecret, TokenTypeAccess)
	if err == nil {
		t.Error("ValidateToken() should fail when token type doesn't match")
	}
}

func TestValidateToken_WrongSecret(t *testing.T) {
	userID := "user-123"

	pair, err := GenerateTokenPair(userID, testSecret)
	if err != nil {
		t.Fatalf("GenerateTokenPair() error = %v", err)
	}

	_, err = ValidateToken(pair.AccessToken, "wrong-secret", TokenTypeAccess)
	if err == nil {
		t.Error("ValidateToken() should fail with wrong secret")
	}
}

func TestValidateToken_InvalidToken(t *testing.T) {
	_, err := ValidateToken("invalid-token-string", testSecret, TokenTypeAccess)
	if err == nil {
		t.Error("ValidateToken() should fail with invalid token")
	}
}

func TestValidateToken_ExpiredToken(t *testing.T) {
	userID := "user-123"

	// Create an expired token manually
	claims := Claims{
		UserID:    userID,
		TokenType: TokenTypeAccess,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(-1 * time.Hour)), // expired 1 hour ago
			IssuedAt:  jwt.NewNumericDate(time.Now().Add(-2 * time.Hour)),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString([]byte(testSecret))
	if err != nil {
		t.Fatalf("Failed to create expired token: %v", err)
	}

	_, err = ValidateToken(tokenString, testSecret, TokenTypeAccess)
	if err == nil {
		t.Error("ValidateToken() should fail with expired token")
	}
}

func TestValidateToken_TokenHasCorrectExpiry(t *testing.T) {
	userID := "user-123"

	pair, err := GenerateTokenPair(userID, testSecret)
	if err != nil {
		t.Fatalf("GenerateTokenPair() error = %v", err)
	}

	// Check access token expiry (should be ~15 minutes)
	accessClaims, err := ValidateToken(pair.AccessToken, testSecret, TokenTypeAccess)
	if err != nil {
		t.Fatalf("ValidateToken() error = %v", err)
	}

	accessExpiry := accessClaims.ExpiresAt.Time
	expectedAccessExpiry := time.Now().Add(AccessTokenDuration)
	// Allow 5 second tolerance
	if accessExpiry.Before(expectedAccessExpiry.Add(-5*time.Second)) || accessExpiry.After(expectedAccessExpiry.Add(5*time.Second)) {
		t.Errorf("Access token expiry = %v, expected around %v", accessExpiry, expectedAccessExpiry)
	}

	// Check refresh token expiry (should be ~7 days)
	refreshClaims, err := ValidateToken(pair.RefreshToken, testSecret, TokenTypeRefresh)
	if err != nil {
		t.Fatalf("ValidateToken() error = %v", err)
	}

	refreshExpiry := refreshClaims.ExpiresAt.Time
	expectedRefreshExpiry := time.Now().Add(RefreshTokenDuration)
	// Allow 5 second tolerance
	if refreshExpiry.Before(expectedRefreshExpiry.Add(-5*time.Second)) || refreshExpiry.After(expectedRefreshExpiry.Add(5*time.Second)) {
		t.Errorf("Refresh token expiry = %v, expected around %v", refreshExpiry, expectedRefreshExpiry)
	}
}
