import type { ReactNode } from "react";
import { useI18n } from "@/i18n/context";

/**
 * Нижняя панель выбранных записей: их число [count] и действия [actions].
 * Скрывается, когда ничего не выбрано.
 */
export function SelectionBar({ count, actions }: { count: number; actions: ReactNode }) {
  const { t } = useI18n();
  if (count === 0) {
    return null;
  }
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t bg-background/95 backdrop-blur lg:left-60">
      <div className="flex max-w-3xl items-center justify-between gap-3 px-4 py-3 lg:px-8">
        <span className="text-sm font-medium" aria-live="polite">
          {t("directory.selected", { count })}
        </span>
        <div className="flex flex-wrap items-center gap-2">{actions}</div>
      </div>
    </div>
  );
}
