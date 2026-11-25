# Сервис клиентов на Swift + Vapor. Сборка статически линкует stdlib, рантайм — slim-образ Ubuntu.
FROM swift:6.4.2-noble AS builder
WORKDIR /build
COPY Package.swift Package.resolved ./
RUN swift package resolve
COPY Sources Sources
COPY Tests Tests
RUN swift build -c release --static-swift-stdlib \
    && cp "$(swift build -c release --show-bin-path)/ClientsService" /build/clients-service

FROM ubuntu:noble
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates tzdata curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/clients-service /usr/local/bin/clients-service
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD curl -fs http://localhost:8080/health || exit 1
ENTRYPOINT ["clients-service"]
