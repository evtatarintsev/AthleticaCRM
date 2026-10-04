import { XIcon } from "lucide-react";
import type { Ref } from "react";
import type { UploadResponse } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { previewKind } from "./attachmentKind";
import { FileCard } from "./FileCard";

/** Вложение для показа в списке. */
export interface AttachmentItem {
  /** Стабильный ключ вложения в списке. */
  readonly key: string;
  /** Подпись: имя файла или название документа. */
  readonly name: string;
  /** Файл с подписанной ссылкой; `null` — метаданные ещё загружаются. */
  readonly file: UploadResponse | null;
}

/**
 * Плитка вложения [item]: миниатюра изображения или карточка файла, под ней подпись.
 * [broken] — изображение не загрузилось, показывается карточка; об ошибке загрузки
 * сообщает [onBroken]. Нажатие вызывает [onOpen]; пока файл не загружен, плитка
 * не нажимается. Кнопка удаления есть, только если передан [onRemove].
 */
export function AttachmentTile({
  item,
  broken,
  buttonRef,
  onOpen,
  onBroken,
  onRemove,
  removeLabel,
}: {
  readonly item: AttachmentItem;
  readonly broken: boolean;
  readonly buttonRef: Ref<HTMLButtonElement>;
  readonly onOpen: () => void;
  readonly onBroken: () => void;
  readonly onRemove: (() => void) | undefined;
  readonly removeLabel: string;
}) {
  const { t } = useI18n();
  const { file, name } = item;
  return (
    <li className="relative flex min-w-0 flex-col gap-1">
      <button
        ref={buttonRef}
        type="button"
        aria-label={t("attachments.open", { name })}
        disabled={file === null}
        onClick={onOpen}
        className="aspect-square w-full overflow-hidden rounded-md border bg-muted outline-none hover:opacity-90 focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-default"
      >
        {file === null ? (
          <Skeleton className="size-full rounded-none" />
        ) : !broken && previewKind(file.contentType) === "image" ? (
          <img
            src={file.url}
            alt=""
            loading="lazy"
            decoding="async"
            onError={onBroken}
            className="size-full object-cover"
          />
        ) : (
          <FileCard name={name} file={file} variant="tile" />
        )}
      </button>
      <span className="truncate text-xs" title={name}>
        {name}
      </span>
      {onRemove !== undefined && (
        <Button
          type="button"
          variant="secondary"
          size="icon-xs"
          aria-label={removeLabel}
          onClick={onRemove}
          className="absolute top-1 right-1 shadow-xs"
        >
          <XIcon aria-hidden />
        </Button>
      )}
    </li>
  );
}
