import { describe, expect, it } from "vitest";
import { monogramOf } from "./monogram";

describe("монограмма клуба", () => {
  it.each([
    ["Фитнес Лайф", "ФЛ"],
    ["Атлетика", "А"],
    ["Клуб «Олимп»", "КО"],
    ["  фитнес   лайф  ", "ФЛ"],
    ["Спорт-Лайф", "СЛ"],
    ["Федерация художественной гимнастики", "ФХ"],
    ["iron gym", "IG"],
    ["«Олимп»", "О"],
  ])("«%s» → «%s»", (name, monogram) => {
    expect(monogramOf(name)).toBe(monogram);
  });
});
