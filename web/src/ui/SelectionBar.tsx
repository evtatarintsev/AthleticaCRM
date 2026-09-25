import { Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";

/** Нижняя панель выбранных записей: их число [count] и удаление ([onDelete]). */
export function SelectionBar({ count, onDelete }: { count: number; onDelete: () => void }) {
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
        <Button variant="destructive" onClick={onDelete}>
          <Trash2Icon aria-hidden />
          {t("directory.deleteSelected")}
        </Button>
      </div>
    </div>
  );
}
