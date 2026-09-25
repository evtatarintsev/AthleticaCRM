import { describe, expect, it } from "vitest";
import { LoginSearchSchema, postLoginTarget } from "./redirect";

/** Куда попадёт пользователь после входа со страницы `/login?redirect=[redirect]`. */
function targetFor(redirect: string, homeMigrated: boolean): string {
  return postLoginTarget(LoginSearchSchema.parse({ redirect }).redirect, homeMigrated);
}

describe("адрес возврата после входа", () => {
  it.each(["https://evil.example/", "//evil.example/", "/\\evil.example", "javascript:alert(1)"])(
    "чужой адрес %s ведёт на главную",
    (redirect) => {
      expect(targetFor(redirect, true)).toBe("/web/");
      expect(targetFor(redirect, false)).toBe("/");
    },
  );

  it("путь своего origin сохраняется и получает базовый путь веб-клиента", () => {
    expect(targetFor("/clients/42?tab=notes", false)).toBe("/web/clients/42?tab=notes");
  });
});

describe("переход после входа без адреса возврата", () => {
  it("ведёт на главную веб-клиента, если она перенесена", () => {
    expect(postLoginTarget(undefined, true)).toBe("/web/");
  });

  it("ведёт на главную KMP-клиента, если главная не перенесена", () => {
    expect(postLoginTarget(undefined, false)).toBe("/");
  });
});
