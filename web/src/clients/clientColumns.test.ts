import { describe, expect, it } from "vitest";
import { availableClientColumns, clientColumnKey, resolveClientColumns } from "./clientColumns";

describe("колонки таблицы клиентов", () => {
  it("пол и дата рождения из сохранённых колонок отбрасываются как неизвестные", () => {
    expect(
      resolveClientColumns(["gender", "birthday", "groups", "memberships", "balance"], []).map(
        clientColumnKey,
      ),
    ).toEqual(["groups", "memberships", "balance"]);
  });

  it("выбор колонок предлагает группы, абонемент, баланс и контакты, но не пол и дату рождения", () => {
    const keys = availableClientColumns([], []).map(clientColumnKey);
    expect(keys.slice(0, 3)).toEqual(["groups", "memberships", "balance"]);
    expect(keys).toContain("contact:PHONE");
    expect(keys).not.toContain("gender");
    expect(keys).not.toContain("birthday");
  });
});
