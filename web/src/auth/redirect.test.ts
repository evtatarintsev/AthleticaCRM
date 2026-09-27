import { describe, expect, it } from "vitest";
import { LoginSearchSchema, postLoginTarget } from "./redirect";

/** Куда попадёт пользователь после входа со страницы `/login?redirect=[redirect]`. */
function targetFor(redirect: string): string {
  return postLoginTarget(LoginSearchSchema.parse({ redirect }).redirect);
}

describe("адрес возврата после входа", () => {
  it.each(["https://evil.example/", "//evil.example/", "/\\evil.example", "javascript:alert(1)"])(
    "чужой адрес %s ведёт на главную",
    (redirect) => {
      expect(targetFor(redirect)).toBe("/");
    },
  );

  it("путь своего origin сохраняется", () => {
    expect(targetFor("/clients/42?tab=notes")).toBe("/clients/42?tab=notes");
  });
});

describe("переход после входа без адреса возврата", () => {
  it("ведёт на главную", () => {
    expect(postLoginTarget(undefined)).toBe("/");
  });
});
