#!/usr/bin/env sh
# Самоподписанный сертификат для dev-nginx (docker-compose.dev.yaml):
# athletica.crm, minio.athletica.crm, console.minio.athletica.crm.
# HTTPS нужен, чтобы страница была secure context (crypto.randomUUID, clipboard и т.п.).
set -eu

DIR="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$DIR"

openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout "$DIR/dev.key" -out "$DIR/dev.crt" \
  -subj "/CN=athletica.crm" \
  -addext "subjectAltName=DNS:athletica.crm,DNS:minio.athletica.crm,DNS:console.minio.athletica.crm" \
  -addext "basicConstraints=critical,CA:true" \
  -addext "extendedKeyUsage=serverAuth"

echo "Сертификат: $DIR/dev.crt"
echo "Добавить в доверенные (macOS):"
echo "  sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain \"$DIR/dev.crt\""
