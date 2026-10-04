import { describe, expect, it } from "vitest";
import { en } from "@/i18n/en";
import { createTranslator } from "@/i18n/messages";
import { ru } from "@/i18n/ru";
import { fileSize } from "./fileSize";

const tRu = createTranslator<typeof ru>("ru", ru);
const tEn = createTranslator<typeof ru>("en", en);
const spaces = (text: string) => text.replace(/\s/g, " ");

describe("размер файла", () => {
  it("меньше килобайта — в байтах", () => {
    expect(fileSize(tRu, 0)).toBe("0 Б");
    expect(fileSize(tRu, 512)).toBe("512 Б");
  });

  it("выбирает крупную единицу по языку", () => {
    expect(fileSize(tRu, 245_760)).toBe("240 КБ");
    expect(fileSize(tEn, 245_760)).toBe("240 KB");
  });

  it("один знак после запятой", () => {
    expect(fileSize(tRu, 5.5 * 1024 * 1024)).toBe("5,5 МБ");
    expect(fileSize(tEn, 5.5 * 1024 * 1024)).toBe("5.5 MB");
    expect(spaces(fileSize(tRu, 1536 * 1024 ** 3))).toBe("1 536 ГБ");
    expect(fileSize(tEn, 2 * 1024 ** 3)).toBe("2 GB");
  });
});
