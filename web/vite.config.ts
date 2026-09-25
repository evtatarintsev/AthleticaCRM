/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/**
 * Новый веб-фронтенд живёт под префиксом /web/ рядом со старым KMP-клиентом (/).
 * В dev-режиме /api проксируется на локальный Ktor: по умолчанию порт 8080,
 * другой адрес задаётся переменной окружения API_PROXY.
 */
export default defineConfig({
  base: "/web/",
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    host: true,
    allowedHosts: ["athletica.crm"],
    proxy: {
      "/api": process.env.API_PROXY ?? "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test-setup.ts"],
  },
});
