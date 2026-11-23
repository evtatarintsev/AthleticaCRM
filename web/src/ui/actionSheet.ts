import { useState } from "react";

/** Вариант взаимоисключающей панели: размеченное объединение по `kind`. */
interface SheetVariant {
  readonly kind: string;
}

/**
 * Состояние набора взаимоисключающих панелей экрана: открыта не больше одной, и какая именно —
 * один вариант [T] вместо отдельного флага на каждую панель.
 */
export interface ActionSheetState<T extends SheetVariant> {
  /** Открытая панель или `null`. */
  readonly sheet: T | null;
  /** Открывает панель [next] вместо текущей. */
  readonly open: (next: T) => void;
  /** Обработчик `onOpenChange` панели [kind]: закрывает её, не трогая открытую позже другую. */
  readonly closeOn: (kind: T["kind"]) => (open: boolean) => void;
}

/** Взаимоисключающие панели экрана; изначально все закрыты. */
export function useActionSheet<T extends SheetVariant>(): ActionSheetState<T> {
  const [sheet, setSheet] = useState<T | null>(null);
  return {
    sheet,
    open: setSheet,
    closeOn: (kind) => (open) => {
      if (!open) {
        setSheet((current) => (current?.kind === kind ? null : current));
      }
    },
  };
}
