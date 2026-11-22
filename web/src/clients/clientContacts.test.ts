import { describe, expect, it } from "vitest";
import {
  contactEntriesOf,
  contactInputsOf,
  removedContactAt,
  withContactAdded,
  withPrimaryAt,
  type ContactFormEntry,
} from "./clientContacts";

/** Строка телефона с комментарием [label] и отметкой [isPrimary]. */
function phone(label: string, isPrimary: boolean): ContactFormEntry {
  return { type: "PHONE", value: label, label, isPrimary };
}

describe("контакты на форме", () => {
  it("пустой клиент даёт одну основную строку телефона", () => {
    expect(contactEntriesOf([])).toEqual([
      { type: "PHONE", value: "", label: "", isPrimary: true },
    ]);
  });

  it("второй телефон добавляется дополнительным, первый email — основным", () => {
    const entries = withContactAdded(withContactAdded([phone("мама", true)], "PHONE"), "EMAIL");
    expect(entries.map((entry) => [entry.type, entry.isPrimary])).toEqual([
      ["PHONE", true],
      ["PHONE", false],
      ["EMAIL", true],
    ]);
  });

  it("выбор основного снимает отметку только внутри типа", () => {
    const entries: readonly ContactFormEntry[] = [
      phone("мама", true),
      { type: "EMAIL", value: "a@b.c", label: "", isPrimary: true },
      phone("папа", false),
    ];
    expect(withPrimaryAt(entries, 2).map((entry) => entry.isPrimary)).toEqual([false, true, true]);
  });

  it("при удалении основного основным становится следующий телефон", () => {
    const entries = removedContactAt([phone("мама", true), phone("папа", false)], 0);
    expect(entries).toEqual([phone("папа", true)]);
  });

  it("удаление дополнительного не меняет основной", () => {
    const entries = removedContactAt([phone("мама", true), phone("папа", false)], 1);
    expect(entries).toEqual([phone("мама", true)]);
  });

  it("на сервер уходят непустые значения, пустой комментарий — null", () => {
    const entries: readonly ContactFormEntry[] = [
      { type: "PHONE", value: " +7900 ", label: " мама ", isPrimary: true },
      { type: "PHONE", value: "+7901", label: "  ", isPrimary: false },
      { type: "EMAIL", value: "  ", label: "", isPrimary: true },
    ];
    expect(contactInputsOf(entries)).toEqual([
      { type: "PHONE", value: "+7900", label: "мама", isPrimary: true },
      { type: "PHONE", value: "+7901", label: null, isPrimary: false },
    ]);
  });
});
