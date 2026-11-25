/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

/**
 * Кабинет клиента отдаётся с корня хоста `clients.<домен>`.
 * В dev-режиме /api проксируется на локальный Ktor: по умолчанию порт 8080,
 * другой адрес задаётся переменной окружения API_PROXY.
 */
export default defineConfig({
  base: "/",
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    port: 5174,
    host: true,
    allowedHosts: ["clients.athletica.crm"],
    proxy: {
      "/api": process.env["API_PROXY"] ?? "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
  },
});
