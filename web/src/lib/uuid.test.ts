import { describe, expect, it } from "vitest";
import { HallIdSchema } from "@/api/generated/contracts";
import { uuidv7 } from "./uuid";

describe("uuidv7", () => {
  it("кодирует время в первых 48 битах, версию 7 и вариант RFC", () => {
    const id = uuidv7(0x0199a0b27c3e, (bytes) => {
      bytes.fill(0xff);
    });
    expect(id).toBe("0199a0b2-7c3e-7fff-bfff-ffffffffffff");
  });

  it("проходит схему идентификатора из контракта", () => {
    expect(HallIdSchema.safeParse(uuidv7()).success).toBe(true);
  });

  it("идентификаторы, созданные позже, сортируются позже", () => {
    expect(uuidv7(1_000) < uuidv7(2_000)).toBe(true);
  });
});
