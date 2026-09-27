/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

/**
 * Веб-фронтенд отдаётся с корня; исторический префикс `/web` сохраняется временными 301 в nginx.
 * В dev-режиме /api проксируется на локальный Ktor: по умолчанию порт 8080,
 * другой адрес задаётся переменной окружения API_PROXY.
 */
const BASE_PATH = "";

export default defineConfig({
  base: "/",
  define: { __BASE_PATH__: JSON.stringify(BASE_PATH) },
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    port: 5173,
    host: true,
    allowedHosts: ["athletica.crm"],
    proxy: {
      "/api": process.env["API_PROXY"] ?? "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
  },
});
