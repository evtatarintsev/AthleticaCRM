import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useI18n } from "@/i18n/context";

/** Свойства диалога подтверждения. */
interface ConfirmDialogProps {
  /** Открыт ли диалог. */
  readonly open: boolean;
  /** Вызывается при закрытии без подтверждения. */
  readonly onOpenChange: (open: boolean) => void;
  /** Вопрос. */
  readonly title: string;
  /** Пояснение последствий. */
  readonly description: string;
  /** Подпись кнопки подтверждения. */
  readonly confirmLabel: string;
  /** Действие; диалог закрывается, когда промис завершится. */
  readonly onConfirm: () => Promise<void>;
}

/** Подтверждение необратимого действия, например удаления. */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  onConfirm,
}: ConfirmDialogProps) {
  const { t } = useI18n();
  const [pending, setPending] = useState(false);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            variant="destructive"
            disabled={pending}
            aria-busy={pending}
            onClick={() => {
              setPending(true);
              void onConfirm().finally(() => {
                setPending(false);
                onOpenChange(false);
              });
            }}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
