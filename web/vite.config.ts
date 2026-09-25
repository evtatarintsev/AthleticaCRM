/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

/**
 * Новый веб-фронтенд живёт под префиксом /web/ рядом со старым KMP-клиентом (/).
 * В dev-режиме /api проксируется на локальный Ktor: по умолчанию порт 8080,
 * другой адрес задаётся переменной окружения API_PROXY.
 */
/** Базовый путь веб-клиента; после переключения `/` на веб-клиент станет `""`. */
const BASE_PATH = "/web";

export default defineConfig({
  base: `${BASE_PATH}/`,
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
