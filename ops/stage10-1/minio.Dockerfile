# Acceptance-only provider, pinned official upstream source, Go module checksum verification.
FROM golang:1.24-alpine AS build
ENV GOMAXPROCS=2 GOFLAGS=-p=2 CGO_ENABLED=0
RUN go install github.com/minio/minio@RELEASE.2025-04-22T22-12-26Z
FROM postgres:16-alpine
COPY --from=build /go/bin/minio /usr/local/bin/minio
USER 65534:65534
ENTRYPOINT ["/usr/local/bin/minio"]
CMD ["server", "/data", "--address", ":9000", "--console-address", ":9001"]
