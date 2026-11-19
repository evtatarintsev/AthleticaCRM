import type { Money } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";

/**
 * Баланс клиента [balance]: отрицательный — красной меткой, нулевой — приглушённым
 * текстом, положительный — обычным. Одно оформление для таблицы и строки на телефоне.
 */
export function ClientBalance({ balance }: { readonly balance: Money }) {
  const { format } = useI18n();
  return (
    <span
      data-balance={balanceKind(balance)}
      className={cn(
        "whitespace-nowrap tabular-nums",
        balance.minorUnits < 0
          ? "inline-flex h-6 items-center rounded-full bg-destructive/10 px-2.5 font-semibold text-destructive"
          : balance.minorUnits === 0
            ? "text-muted-foreground"
            : "font-medium",
      )}
    >
      {format.money(balance)}
    </span>
  );
}

/** Знак баланса [balance]: долг, ноль или переплата. */
function balanceKind(balance: Money): "neg" | "zero" | "pos" {
  return balance.minorUnits < 0 ? "neg" : balance.minorUnits === 0 ? "zero" : "pos";
}
