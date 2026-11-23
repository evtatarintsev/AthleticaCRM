import { request } from "@playwright/test";

/** Сколько ждать, пока сервер стенда поднимется и применит миграции. */
const READY_TIMEOUT_MS = 180_000;

/**
 * Ждёт готовности стенда: `GET /api/auth/me` через боевой nginx отвечает 401.
 * Health-эндпоинта у сервера нет, а 401 доказывает, что nginx, сервер и база отвечают.
 */
export default async function globalSetup(): Promise<void> {
  const context = await request.newContext({
    baseURL: "https://app.e2e.test",
    ignoreHTTPSErrors: true,
  });
  const deadline = Date.now() + READY_TIMEOUT_MS;
  let last = "нет ответа";
  while (Date.now() < deadline) {
    try {
      const response = await context.get("/api/auth/me");
      if (response.status() === 401) {
        await context.dispose();
        return;
      }
      last = `HTTP ${String(response.status())}`;
    } catch (error) {
      last = String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  await context.dispose();
  throw new Error(`Стенд не готов за ${String(READY_TIMEOUT_MS / 1000)} с: ${last}`);
}
