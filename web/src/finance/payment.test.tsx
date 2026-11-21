import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { Button } from "@/components/ui/button";
import { ru } from "@/i18n/ru";
import { cashAccount, bankAccount } from "@/test/finance";
import { renderPage } from "@/test/render";
import { PaymentBlock } from "./PaymentBlock";
import { initialPayment, paymentInput } from "./payment";

const debt = { minorUnits: 450000, currency: "RUB" as const };
const advance = { ...debt, minorUnits: 100000 };

function PaymentHarness({ permitted = true }: { readonly permitted?: boolean }) {
  const [value, setValue] = useState(initialPayment);
  const [due, setDue] = useState(debt);
  return (
    <>
      <PaymentBlock
        value={value}
        onChange={setValue}
        debt={due}
        advance={advance}
        accounts={[cashAccount, bankAccount]}
        canAccept={permitted}
      />
      <Button
        type="button"
        onClick={() => {
          setDue({ ...debt, minorUnits: 350000 });
        }}
      >
        Стоимость 3500
      </Button>
    </>
  );
}

describe("общий блок оплаты", () => {
  it("смешанная оплата и переплата сохраняют точные суммы частей", () => {
    const input = paymentInput(
      {
        cash: [
          { key: "a", cashAccountId: cashAccount.id, amount: "2000" },
          { key: "b", cashAccountId: bankAccount.id, amount: "3000" },
        ],
        fromAdvance: "",
      },
      [cashAccount, bankAccount],
      debt,
      advance,
    );
    expect(input?.cash?.map((c) => c.amount.minorUnits)).toEqual([200000, 300000]);
    expect(
      paymentInput({ ...initialPayment(), fromAdvance: "1001" }, [cashAccount], debt, advance),
    ).toBeNull();
    expect(
      paymentInput({ ...initialPayment(), fromAdvance: "4501" }, [cashAccount], debt, {
        ...advance,
        minorUnits: 500000,
      }),
    ).toBeNull();
  });

  it("автоматическая кассовая часть следует за стоимостью и авансом", async () => {
    renderPage(<PaymentHarness />);
    const user = userEvent.setup();
    const amount = await screen.findByLabelText(ru["finance.amount"]);
    expect(amount).toHaveValue("4500");
    await user.click(screen.getByRole("button", { name: "Стоимость 3500" }));
    expect(amount).toHaveValue("3500");
    await user.type(screen.getByLabelText(new RegExp(ru["finance.fromAdvance"])), "1000");
    expect(amount).toHaveValue("2500");
  });

  it("показывает переплату и позволяет распределить оплату между кассами", async () => {
    renderPage(<PaymentHarness />);
    const user = userEvent.setup();
    const amount = await screen.findByLabelText(ru["finance.amount"]);
    await user.clear(amount);
    await user.type(amount, "5000");
    expect(await screen.findByRole("status")).toHaveTextContent(/500.*будет зачислено/);
    await user.click(screen.getByRole("button", { name: ru["finance.addPart"] }));
    expect(screen.getAllByLabelText(ru["finance.cashAccount"])).toHaveLength(2);
  });

  it("без права приёма оплат блок скрыт", async () => {
    renderPage(<PaymentHarness permitted={false} />);
    await screen.findByRole("button", { name: "Стоимость 3500" });
    expect(screen.queryByLabelText(ru["finance.amount"])).not.toBeInTheDocument();
  });
});
