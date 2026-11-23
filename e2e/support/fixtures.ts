import { randomUUID } from "node:crypto";
import { test as base, type APIRequestContext, type Page } from "@playwright/test";
import { call } from "./api";
import { t } from "./i18n";

/** Владелец организации, зарегистрированной для одного сценария. */
export interface Owner {
  /** Логин и адрес почты владельца, уникальный на сценарий. */
  readonly email: string;
  /** Пароль владельца. */
  readonly password: string;
  /** Имя владельца. */
  readonly name: string;
  /** Название организации. */
  readonly companyName: string;
  /** Клиент API с сессией владельца: для подготовки данных сценария. */
  readonly api: APIRequestContext;
}

/** Пароль владельцев тестовых организаций. */
export const OWNER_PASSWORD = "E2e-password-1";

/** Уникальный адрес почты на домене стенда с префиксом [prefix]. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${randomUUID()}@e2e.test`;
}

/**
 * Тест со своей организацией: фикстура `owner` регистрирует её через API, а сессия владельца
 * действует и в `request`, и в браузерном `context` — сценарий начинается в кабинете.
 */
export const test = base.extend<{ owner: Owner }>({
  owner: async ({ request, context }, use) => {
    const id = randomUUID().slice(0, 8);
    const owner: Owner = {
      email: uniqueEmail("owner"),
      password: OWNER_PASSWORD,
      name: `Владелец ${id}`,
      companyName: `Клуб ${id}`,
      api: request,
    };
    await call(request, "auth/sign-up", {
      companyName: owner.companyName,
      userName: owner.name,
      login: owner.email,
      password: owner.password,
      timezone: "Europe/Moscow",
      currency: "RUB",
    });
    await context.addCookies((await request.storageState()).cookies);
    await use(owner);
  },
});

/** Вход по логину [email] и паролю [password] на открытой странице входа. */
export async function logIn(page: Page, email: string, password: string): Promise<void> {
  await page.getByLabel(t["auth.email"]).fill(email);
  await page.getByLabel(t["auth.password"], { exact: true }).fill(password);
  await page.getByRole("button", { name: t["auth.actionLogin"] }).click();
}

export { expect } from "@playwright/test";
