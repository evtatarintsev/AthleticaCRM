import { describe, expect, it } from "vitest";
import { SportCatalogResponseSchema } from "@/api/generated/contracts";
import { catalogSuggestions } from "@/settings/sportCatalog";
import { filterSuggestions, markExisting, normalize } from "./nameSuggestions";

const catalog = SportCatalogResponseSchema.parse({
  sports: [
    {
      key: "aquatics",
      names: { ru: "Плавание", en: "Swimming" },
      disciplines: [
        { key: "swimming", names: { ru: "Плавание", en: "Swimming" }, aliases: [] },
        {
          key: "artistic_swimming",
          names: { ru: "Синхронное плавание", en: "Artistic swimming" },
          aliases: ["синхронка"],
        },
      ],
    },
    {
      key: "athletics",
      names: { ru: "Лёгкая атлетика", en: "Athletics" },
      disciplines: [
        {
          key: "track_and_field",
          names: { ru: "Лёгкая атлетика", en: "Track and field" },
          aliases: ["athletics"],
        },
      ],
    },
    {
      key: "dance",
      names: { ru: "Танцы", en: "Dance" },
      disciplines: [
        {
          key: "breaking",
          names: { ru: "Брейкинг", en: "Breaking" },
          aliases: ["брейк-данс", "break dance", "b-boying"],
        },
        { key: "jazz_funk", names: { ru: "Jazz-funk", en: "Jazz-funk" }, aliases: ["джаз-фанк"] },
      ],
    },
  ],
});

const ru = catalogSuggestions(catalog, "ru");

/** Подписи подошедших подсказок по группам. */
function labels(input: string) {
  return filterSuggestions(ru, input).map((g) => ({
    group: g.group.label,
    items: g.items.map((i) => i.suggestion.label),
  }));
}

describe("normalize", () => {
  it("приводит регистр, ё, дефисы и пробелы к одному виду", () => {
    expect(normalize("  Jazz-funk ")).toBe("jazz funk");
    expect(normalize("Jazz   funk")).toBe("jazz funk");
    expect(normalize("ЛЁГКАЯ")).toBe("легкая");
    expect(normalize("snake_case")).toBe("snake case");
  });
});

describe("catalogSuggestions", () => {
  it("подписывает на языке интерфейса и ищет по всем языкам и синонимам", () => {
    const en = catalogSuggestions(catalog, "en");
    expect(en[2]?.label).toBe("Dance");
    expect(en[2]?.items[0]?.label).toBe("Breaking");
    expect(ru[2]?.items[0]?.terms).toEqual([
      "Брейкинг",
      "Breaking",
      "брейк-данс",
      "break dance",
      "b-boying",
    ]);
  });
});

describe("filterSuggestions", () => {
  it("пустой ввод оставляет весь каталог в исходном порядке", () => {
    expect(labels("")).toEqual([
      { group: "Плавание", items: ["Плавание", "Синхронное плавание"] },
      { group: "Лёгкая атлетика", items: ["Лёгкая атлетика"] },
      { group: "Танцы", items: ["Брейкинг", "Jazz-funk"] },
    ]);
  });

  it("находит по латинице в RU-интерфейсе и показывает сработавшее написание", () => {
    const matched = filterSuggestions(ru, "break");
    expect(matched.map((g) => g.group.label)).toEqual(["Танцы"]);
    expect(matched[0]?.items).toEqual([{ suggestion: ru[2]?.items[0], hint: "Breaking" }]);
  });

  it("находит по синониму", () => {
    const matched = filterSuggestions(ru, "брейк-д");
    expect(matched[0]?.items[0]?.hint).toBe("брейк-данс");
  });

  it("находит по названию на другом языке", () => {
    const matched = filterSuggestions(ru, "swim");
    expect(matched.map((g) => g.items.map((i) => [i.suggestion.label, i.hint]))).toEqual([
      [
        ["Плавание", "Swimming"],
        ["Синхронное плавание", "Artistic swimming"],
      ],
    ]);
  });

  it("совпадение по своему названию — без пометки", () => {
    expect(filterSuggestions(ru, "плав")[0]?.items[0]?.hint).toBeNull();
  });

  it("нормализует ввод", () => {
    expect(labels("Jazz funk")).toEqual([{ group: "Танцы", items: ["Jazz-funk"] }]);
    expect(labels("ЛЕГКАЯ")).toEqual([{ group: "Лёгкая атлетика", items: ["Лёгкая атлетика"] }]);
  });

  it("ничего не находит", () => {
    expect(filterSuggestions(ru, "керлинг")).toEqual([]);
  });
});

describe("markExisting", () => {
  it("помечает совпадение по названию", () => {
    expect(markExisting(ru, ["Плавание"]).get("swimming")).toBe("Плавание");
  });

  it("помечает совпадение по синониму без учёта регистра", () => {
    const existing = markExisting(ru, ["break dance", "Футбол"]);
    expect(existing.get("breaking")).toBe("break dance");
    expect(existing.size).toBe(1);
  });

  it("сравнивает после нормализации", () => {
    expect(markExisting(ru, ["jazz funk"]).get("jazz_funk")).toBe("jazz funk");
  });
});
