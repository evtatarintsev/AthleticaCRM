import { describe, expect, it } from "vitest";
import { groupListEmptyState } from "./groupListSearch";

describe("пустое состояние списка групп", () => {
  it("пустой филиал — «групп нет» и без фильтров, и с фильтром из адреса", () => {
    expect(groupListEmptyState(0, 0)).toBe("no-groups");
  });

  it("группы есть, но ни одна не подошла — «ничего не найдено»", () => {
    expect(groupListEmptyState(9, 0)).toBe("no-results");
  });

  it("есть показанные группы — пустого состояния нет", () => {
    expect(groupListEmptyState(9, 2)).toBeNull();
  });
});
