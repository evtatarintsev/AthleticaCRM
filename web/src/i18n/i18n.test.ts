import { describe, expect, expectTypeOf, it } from "vitest";
import {
  MoneySchema,
  InstantSchema,
  LocalDateSchema,
  LocalTimeSchema,
} from "@/api/generated/contracts";
import { en } from "./en";
import { createFormatters } from "./format";
import { resolveLocale } from "./locale";
import { createTranslator, type ArgsOf, type Messages } from "./messages";
import { ru } from "./ru";

describe("выбор языка", () => {
  it("английский браузер получает английский интерфейс", () => {
    expect(resolveLocale(null, "en-US")).toBe("en");
  });

  it("неподдерживаемый язык браузера даёт русский", () => {
    expect(resolveLocale(null, "de-DE")).toBe("ru");
  });

  it("выбор пользователя важнее языка браузера, неизвестный выбор игнорируется", () => {
    expect(resolveLocale("en", "ru-RU")).toBe("en");
    expect(resolveLocale("de", "en-GB")).toBe("en");
  });
});

describe("перевод", () => {
  it("подставляет параметры", () => {
    expect(createTranslator<typeof ru>("ru", ru)("branch.switched", { name: "Центр" })).toBe(
      "Филиал «Центр»",
    );
    expect(createTranslator<typeof ru>("en", en)("branch.switched", { name: "North" })).toBe(
      "Branch “North”",
    );
  });

  it("выбирает форму числа по правилам языка", () => {
    const source = {
      clients: {
        one: "{count} клиент",
        few: "{count} клиента",
        many: "{count} клиентов",
        other: "{count} клиента",
      },
    } as const;
    const english: Messages<typeof source> = {
      clients: { one: "{count} client", other: "{count} clients" },
    };
    const t = createTranslator<typeof source>("ru", source);
    expect([1, 3, 5, 21, 1.5].map((count) => t("clients", { count }))).toEqual([
      "1 клиент",
      "3 клиента",
      "5 клиентов",
      "21 клиент",
      "1,5 клиента",
    ]);
    const tEn = createTranslator<typeof source>("en", english);
    expect([1, 1000].map((count) => tEn("clients", { count }))).toEqual([
      "1 client",
      "1,000 clients",
    ]);
  });

  it("параметры сообщения выводятся из русского словаря", () => {
    expectTypeOf<ArgsOf<(typeof ru)["auth.email"]>>().toEqualTypeOf<[]>();
    expectTypeOf<ArgsOf<(typeof ru)["branch.switched"]>>().toEqualTypeOf<
      [params: Readonly<Record<"name", string | number>>]
    >();
  });

  it("словарь без ключа из источника не подходит по типу", () => {
    expectTypeOf<{ readonly a: string }>().not.toExtend<Messages<{ a: "x"; b: "y" }>>();
  });
});

describe("форматирование", () => {
  const ruFormat = createFormatters("ru");
  const enFormat = createFormatters("en");
  const spaces = (text: string) => text.replace(/\s/g, " ");

  it("деньги — по минорным единицам, без потери точности", () => {
    expect(spaces(ruFormat.money(MoneySchema.parse({ minorUnits: 120050, currency: "RUB" })))).toBe(
      "1 200,50 ₽",
    );
    expect(enFormat.money(MoneySchema.parse({ minorUnits: -5, currency: "USD" }))).toBe("-$0.05");
    expect(
      enFormat.money(MoneySchema.parse({ minorUnits: 9007199254740991, currency: "USD" })),
    ).toBe("$90,071,992,547,409.91");
  });

  it("календарная дата не сдвигается часовым поясом", () => {
    expect(enFormat.date(LocalDateSchema.parse("2025-01-31"))).toBe("Jan 31, 2025");
  });

  it("время суток и момент времени", () => {
    expect(spaces(enFormat.time(LocalTimeSchema.parse("18:30:00")))).toBe("6:30 PM");
    expect(ruFormat.dateTime(InstantSchema.parse("2025-01-31T09:05:00Z"))).toContain("2025");
  });

  it("относительное время: секунды, минуты, часы, дни, старше недели — дата", () => {
    const now = new Date("2025-02-10T12:00:00Z");
    const at = (iso: string) => InstantSchema.parse(iso);
    expect(enFormat.ago(at("2025-02-10T11:59:30Z"), now)).toBe("now");
    expect(ruFormat.ago(at("2025-02-10T11:55:00Z"), now)).toBe("5 минут назад");
    expect(enFormat.ago(at("2025-02-10T09:00:00Z"), now)).toBe("3 hours ago");
    expect(enFormat.ago(at("2025-02-08T12:00:00Z"), now)).toBe("2 days ago");
    expect(enFormat.ago(at("2025-01-31T12:00:00Z"), now)).toBe("Jan 31, 2025");
  });
});
