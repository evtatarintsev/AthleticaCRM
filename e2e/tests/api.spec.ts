import { request as playwrightRequest } from "@playwright/test";
import { call } from "../support/api";
import { expect, test } from "../support/fixtures";

test.describe("API без сессии", () => {
  test("защищённые эндпоинты отвечают 401 и не раскрывают данных", async ({ owner }) => {
    const anonymous = await playwrightRequest.newContext({
      baseURL: "https://app.e2e.test",
      ignoreHTTPSErrors: true,
    });
    const me = await anonymous.get("/api/auth/me");
    const clients = await anonymous.post("/api/clients/list", { data: {} });

    expect(me.status()).toBe(401);
    expect(clients.status()).toBe(401);
    expect(await me.text()).not.toContain(owner.email);
    await anonymous.dispose();
  });

  test("сессия, потерявшая cookie, получает 401", async ({ owner, request }) => {
    expect((await call(request, "auth/me", undefined)).username).toBe(owner.email);

    const withoutCookies = await playwrightRequest.newContext({
      baseURL: "https://app.e2e.test",
      ignoreHTTPSErrors: true,
      storageState: { cookies: [], origins: [] },
    });
    expect((await withoutCookies.get("/api/auth/me")).status()).toBe(401);
    await withoutCookies.dispose();
  });
});

test("каждый сценарий получает свою организацию", async ({ owner, request }) => {
  const second = await playwrightRequest.newContext({
    baseURL: "https://app.e2e.test",
    ignoreHTTPSErrors: true,
  });
  const secondEmail = `second-${owner.email}`;
  await call(second, "auth/sign-up", {
    companyName: "Второй клуб",
    userName: "Второй владелец",
    login: secondEmail,
    password: owner.password,
    timezone: "Europe/Moscow",
    currency: "RUB",
  });

  const first = await call(request, "auth/me", undefined);
  const other = await call(second, "auth/me", undefined);

  expect(first.orgInfo.name).toBe(owner.companyName);
  expect(other.orgInfo.name).toBe("Второй клуб");
  expect(other.currentBranch.id).not.toBe(first.currentBranch.id);
  await second.dispose();
});
