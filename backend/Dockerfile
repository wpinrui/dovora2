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
    ffmpeg \
    python3 \
    py3-pip \
    && pip3 install --break-system-packages yt-dlp \
    && rm -rf /var/cache/apk/*

# Create downloads directory
RUN mkdir -p /app/downloads

# Copy binary from builder
COPY --from=builder /app/server .

# Environment variables
ENV PORT=8080
ENV DOWNLOADS_DIR=/app/downloads

EXPOSE 8080

CMD ["./server"]
