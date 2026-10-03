import {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { ConfirmDialog } from "./ConfirmDialog";
import {
  EditSheetContext,
  useEditSheet,
  type EditSheetControl,
  type EditSheetGuard,
} from "./editSheetContext";

/** Ширина панели на широком экране; на узком панель всегда во всю ширину. */
export type EditSheetSize = "md" | "lg" | "xl";

/** Классы ширины для каждого размера панели. */
const SIZE_CLASSES: Readonly<Record<EditSheetSize, string>> = {
  md: "sm:max-w-md",
  lg: "sm:max-w-2xl",
  xl: "sm:max-w-4xl",
};

/**
 * Esc пришёл из поля с раскрытым списком подсказок: его закрывает само поле, а панель
 * остаётся открытой. Radix ловит Esc на документе раньше поля, поэтому проверка — здесь.
 */
function isExpandedCombobox(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    target.getAttribute("role") === "combobox" &&
    target.getAttribute("aria-expanded") === "true"
  );
}

/** Свойства панели редактирования. */
interface EditSheetProps {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Заголовок панели. */
  readonly title: string;
  /** Ширина: форма — `md` (по умолчанию), список или таблица — шире. */
  readonly size?: EditSheetSize;
  /**
   * Содержимое; монтируется только пока панель открыта, поэтому каждое открытие
   * начинается с чистой формы.
   */
  readonly children: ReactNode;
}

/**
 * Панель редактирования справа (side sheet). В отличие от панели уведомлений, клик мимо
 * её не закрывает, а закрытие крестиком, Esc или «Отменой» при несохранённых изменениях
 * требует подтверждения. Панели можно открывать одну поверх другой: вложенная [EditSheet]
 * внутри содержимого сдвигает родительскую влево, и под верхней панелью видно, откуда она открыта.
 */
export function EditSheet({ open, onOpenChange, title, size = "md", children }: EditSheetProps) {
  const { t } = useI18n();
  const parent = useContext(EditSheetContext);
  const [childOpen, setChildOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const guard = useRef<EditSheetGuard>({ dirty: false, submitting: false });

  useEffect(() => {
    if (parent === null) {
      return;
    }
    parent.reportChild(open);
    return () => {
      parent.reportChild(false);
    };
  }, [parent, open]);

  const close = useCallback(() => {
    guard.current = { dirty: false, submitting: false };
    onOpenChange(false);
  }, [onOpenChange]);

  const requestClose = useCallback(() => {
    if (guard.current.submitting) {
      return;
    }
    if (guard.current.dirty) {
      setConfirming(true);
    } else {
      close();
    }
  }, [close]);

  const reportGuard = useCallback((next: EditSheetGuard) => {
    guard.current = next;
  }, []);

  const control = useMemo<EditSheetControl>(
    () => ({ close, requestClose, reportGuard, reportChild: setChildOpen }),
    [close, requestClose, reportGuard],
  );

  return (
    <Sheet
      open={open}
      onOpenChange={(next) => {
        if (next) {
          onOpenChange(true);
        } else {
          requestClose();
        }
      }}
    >
      <SheetContent
        side="right"
        closeLabel={t("action.close")}
        aria-describedby={undefined}
        onInteractOutside={(event) => {
          event.preventDefault();
        }}
        onEscapeKeyDown={(event) => {
          if (isExpandedCombobox(event.target)) {
            event.preventDefault();
          }
        }}
        className={cn("w-full gap-0", SIZE_CLASSES[size], childOpen && "sm:-translate-x-20")}
      >
        <SheetHeader className="border-b pr-12">
          <SheetTitle>{title}</SheetTitle>
        </SheetHeader>
        <EditSheetContext.Provider value={control}>{children}</EditSheetContext.Provider>
        <ConfirmDialog
          open={confirming}
          onOpenChange={setConfirming}
          title={t("editSheet.discardTitle")}
          description={t("editSheet.discardDescription")}
          confirmLabel={t("editSheet.discardConfirm")}
          onConfirm={() => {
            close();
            return Promise.resolve();
          }}
        />
      </SheetContent>
    </Sheet>
  );
}

/** Свойства формы внутри панели. */
interface EditSheetFormProps {
  /** Есть несохранённые изменения. */
  readonly dirty: boolean;
  /** Идёт сохранение: кнопки недоступны, панель не закрывается. */
  readonly submitting: boolean;
  /** Отправка запрещена: кнопка «Сохранить» недоступна, Enter форму не отправляет. */
  readonly submitDisabled?: boolean;
  /** Отправка формы кнопкой «Сохранить» или Enter. */
  readonly onSubmit: () => void;
  /** Подпись кнопки отправки вместо «Сохранить». */
  readonly submitLabel?: string;
  /** Поля формы; прокручиваются, кнопки остаются внизу. */
  readonly children: ReactNode;
  /** Вложенные панели, открываемые из формы; рендерятся вне элемента `<form>`. */
  readonly nested?: ReactNode;
}

/** Форма панели [EditSheet]: прокручиваемые поля и закреплённые внизу «Сохранить» / «Отмена». */
export function EditSheetForm({
  dirty,
  submitting,
  submitDisabled = false,
  onSubmit,
  submitLabel,
  children,
  nested,
}: EditSheetFormProps) {
  const { t } = useI18n();
  const { requestClose, reportGuard } = useEditSheet();

  useEffect(() => {
    reportGuard({ dirty, submitting });
  }, [reportGuard, dirty, submitting]);

  return (
    <>
      <form
        method="post"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          if (!submitDisabled) {
            onSubmit();
          }
        }}
        className="flex min-h-0 flex-1 flex-col"
      >
        <EditSheetBody>{children}</EditSheetBody>
        <SheetFooter className="flex-row justify-end border-t">
          <Button type="button" variant="outline" disabled={submitting} onClick={requestClose}>
            {t("action.cancel")}
          </Button>
          <Button type="submit" disabled={submitting || submitDisabled} aria-busy={submitting}>
            {submitLabel ?? t("action.save")}
          </Button>
        </SheetFooter>
      </form>
      {nested}
    </>
  );
}

/** Прокручиваемое содержимое панели между заголовком и нижней строкой. */
export function EditSheetBody({ children }: { children: ReactNode }) {
  return <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">{children}</div>;
}

/**
 * Нижняя строка панели со списком: число выбранных записей [count] и действия над ними
 * [actions]. Заменяет `SelectionBar` страницы, который внутри панели был бы под затемнением.
 * Скрывается, когда ничего не выбрано.
 */
export function EditSheetSelection({ count, actions }: { count: number; actions: ReactNode }) {
  const { t } = useI18n();
  if (count === 0) {
    return null;
  }
  return (
    <SheetFooter className="flex-row items-center justify-between border-t">
      <span className="text-sm font-medium" aria-live="polite">
        {t("directory.selected", { count })}
      </span>
      <div className="flex flex-wrap items-center gap-2">{actions}</div>
    </SheetFooter>
  );
}
