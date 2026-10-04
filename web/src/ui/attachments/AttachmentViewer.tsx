import { ChevronLeftIcon, ChevronRightIcon, ExternalLinkIcon } from "lucide-react";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { buttonVariants } from "@/components/ui/button-variants";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { previewKind } from "./attachmentKind";
import type { AttachmentItem } from "./AttachmentTile";
import { FileCard } from "./FileCard";

/**
 * Просмотрщик вложений [items] поверх страницы, открытый на позиции [index]; `null` —
 * закрыт. [broken] — ключи вложений, которые не удалось загрузить: для них показывается
 * карточка файла; новую ошибку загрузки сообщает [onBroken]. Переход к соседнему
 * вложению — [onIndexChange] (кнопками и клавишами ←/→ на всём окне: фокус может уйти
 * со ставшей недоступной кнопки), закрытие — [onClose]. [onCloseAutoFocus] возвращает фокус
 * на страницу после закрытия.
 */
export function AttachmentViewer({
  items,
  index,
  broken,
  onBroken,
  onIndexChange,
  onClose,
  onCloseAutoFocus,
}: {
  readonly items: readonly AttachmentItem[];
  readonly index: number | null;
  readonly broken: ReadonlySet<string>;
  readonly onBroken: (key: string) => void;
  readonly onIndexChange: (index: number) => void;
  readonly onClose: () => void;
  readonly onCloseAutoFocus: (event: Event) => void;
}) {
  const { t } = useI18n();
  const item = index === null ? undefined : items[index];
  const current = index ?? 0;
  const hasPrevious = current > 0;
  const hasNext = current < items.length - 1;

  const open = item !== undefined;

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLMediaElement) {
        return;
      }
      if (event.key === "ArrowLeft" && hasPrevious) {
        onIndexChange(current - 1);
      }
      if (event.key === "ArrowRight" && hasNext) {
        onIndexChange(current + 1);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open, current, hasPrevious, hasNext, onIndexChange]);

  return (
    <Dialog
      open={open}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent
        closeLabel={t("action.close")}
        aria-describedby={undefined}
        onCloseAutoFocus={onCloseAutoFocus}
        className="top-0 left-0 flex h-dvh w-full max-w-none translate-x-0 translate-y-0 flex-col gap-0 overflow-hidden rounded-none p-0 data-[state=closed]:zoom-out-100 data-[state=open]:zoom-in-100 sm:top-6 sm:left-6 sm:h-[calc(100dvh-3rem)] sm:w-[calc(100%-3rem)] sm:max-w-none sm:rounded-lg"
      >
        <DialogTitle className="sr-only">{t("attachments.viewerTitle")}</DialogTitle>
        {item !== undefined && (
          <>
            <div className="flex min-h-14 items-center gap-3 border-b py-2 pr-12 pl-4">
              <span className="min-w-0 flex-1 truncate text-sm font-medium" title={item.name}>
                {item.name}
              </span>
              <span className="shrink-0 text-sm text-muted-foreground">
                {t("attachments.position", { index: current + 1, total: items.length })}
              </span>
              {item.file !== null && (
                <a
                  href={item.file.url}
                  target="_blank"
                  rel="noreferrer"
                  aria-label={t("attachments.openInNewTab")}
                  title={t("attachments.openInNewTab")}
                  className={cn(buttonVariants({ variant: "ghost", size: "icon-sm" }), "shrink-0")}
                >
                  <ExternalLinkIcon aria-hidden />
                </a>
              )}
            </div>
            <div className="flex min-h-0 flex-1 items-center justify-center bg-muted p-2 sm:p-4">
              <ViewerContent
                key={item.key}
                item={item}
                broken={broken.has(item.key)}
                onBroken={() => {
                  onBroken(item.key);
                }}
              />
            </div>
            {items.length > 1 && (
              <div className="flex items-center justify-between border-t px-4 py-2">
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label={t("attachments.previous")}
                  disabled={!hasPrevious}
                  onClick={() => {
                    onIndexChange(current - 1);
                  }}
                >
                  <ChevronLeftIcon aria-hidden />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label={t("attachments.next")}
                  disabled={!hasNext}
                  onClick={() => {
                    onIndexChange(current + 1);
                  }}
                >
                  <ChevronRightIcon aria-hidden />
                </Button>
              </div>
            )}
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * Содержимое просмотрщика для вложения [item] по его типу. Монтируется заново при смене
 * вложения, поэтому видео и аудио останавливаются сами. [broken] или ошибка загрузки
 * ([onBroken]) — карточка файла вместо содержимого.
 */
function ViewerContent({
  item,
  broken,
  onBroken,
}: {
  readonly item: AttachmentItem;
  readonly broken: boolean;
  readonly onBroken: () => void;
}) {
  const { file, name } = item;
  if (file === null) {
    return <Skeleton className="aspect-square w-full max-w-sm" />;
  }
  const card = <FileCard name={name} file={file} variant="viewer" />;
  if (broken) {
    return card;
  }
  switch (previewKind(file.contentType)) {
    case "image":
      return (
        <img
          src={file.url}
          alt={name}
          onError={onBroken}
          className="max-h-full max-w-full object-contain"
        />
      );
    case "pdf":
      return inlinePdfSupported() ? (
        <iframe src={file.url} title={name} className="size-full rounded-md border bg-background" />
      ) : (
        card
      );
    case "video":
      return (
        <video
          src={file.url}
          controls
          preload="metadata"
          onError={onBroken}
          className="max-h-full max-w-full"
        />
      );
    case "audio":
      return <audio src={file.url} controls onError={onBroken} className="w-full max-w-md" />;
    case "file":
      return card;
  }
}

/**
 * Истина, если браузер показывает PDF встроенно. Браузеры без свойства
 * `navigator.pdfViewerEnabled` (старые версии) считаются умеющими: так было до его появления.
 */
function inlinePdfSupported(): boolean {
  return "pdfViewerEnabled" in navigator ? navigator.pdfViewerEnabled : true;
}
