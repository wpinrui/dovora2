# Build stage
FROM golang:1.23-alpine AS builder

WORKDIR /app

# Install build dependencies
RUN apk add --no-cache git

# Copy go mod files first for better caching
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the binary
RUN CGO_ENABLED=0 GOOS=linux go build -o server ./cmd/server

# Runtime stage
FROM alpine:3.20

WORKDIR /app

# Install runtime dependencies
RUN apk add --no-cache \
    ca-certificates \
    curl \
    ffmpeg \
    python3 \
    py3-pip \
    && pip3 install --break-system-packages yt-dlp \
    && rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -S dovora && adduser -S dovora -G dovora

# Create downloads directory with correct ownership
RUN mkdir -p /app/downloads && chown -R dovora:dovora /app

# Copy binary from builder
COPY --from=builder /app/server .

# Environment variables
ENV PORT=8080
ENV DOWNLOADS_DIR=/app/downloads

# Switch to non-root user
USER dovora

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

CMD ["./server"]
