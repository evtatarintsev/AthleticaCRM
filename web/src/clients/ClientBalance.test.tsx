import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MoneySchema } from "@/api/generated/contracts";
import { renderPage } from "@/test/render";
import { ClientBalance } from "./ClientBalance";

describe("баланс клиента", () => {
  it.each([
    [-120_000, "neg", /[-−]1\s200,00\s₽/],
    [0, "zero", /0,00\s₽/],
    [240_000, "pos", /2\s400,00\s₽/],
  ] as const)("%i — %s", async (minorUnits, kind, text) => {
    renderPage(<ClientBalance balance={MoneySchema.parse({ minorUnits, currency: "RUB" })} />);
    expect(await screen.findByText(text)).toHaveAttribute("data-balance", kind);
  });
});
