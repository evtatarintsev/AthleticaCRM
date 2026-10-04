import {
  ExternalLinkIcon,
  FileIcon,
  FileImageIcon,
  FileMusicIcon,
  FilePlayIcon,
  FileTextIcon,
} from "lucide-react";
import type { UploadResponse } from "@/api/generated/contracts";
import { buttonVariants } from "@/components/ui/button-variants";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { previewKind, type PreviewKind } from "./attachmentKind";
import { fileSize } from "./fileSize";

/** Иконка типа файла для способа показа [kind] с классами [className]. */
function FileKindIcon({
  kind,
  className,
}: {
  readonly kind: PreviewKind;
  readonly className: string;
}) {
  switch (kind) {
    case "image":
      return <FileImageIcon aria-hidden className={className} />;
    case "pdf":
      return <FileTextIcon aria-hidden className={className} />;
    case "video":
      return <FilePlayIcon aria-hidden className={className} />;
    case "audio":
      return <FileMusicIcon aria-hidden className={className} />;
    case "file":
      return <FileIcon aria-hidden className={className} />;
  }
}

/**
 * Карточка файла [file] с подписью [name]: иконка типа и размер. Вариант `tile` заполняет
 * плитку списка; вариант `viewer` — содержимое просмотрщика для файлов, которые нельзя
 * показать встроенно, с полным именем и ссылкой «Открыть в новой вкладке».
 */
export function FileCard({
  name,
  file,
  variant,
}: {
  readonly name: string;
  readonly file: UploadResponse;
  readonly variant: "tile" | "viewer";
}) {
  const { t } = useI18n();
  const kind = previewKind(file.contentType);
  const size = fileSize(t, file.sizeBytes);
  if (variant === "tile") {
    return (
      <span className="flex size-full flex-col items-center justify-center gap-1 p-2 text-muted-foreground">
        <FileKindIcon kind={kind} className="size-8" />
        <span className="text-xs">{size}</span>
      </span>
    );
  }
  return (
    <div className="flex max-w-full flex-col items-center gap-3 rounded-lg border bg-background p-6 text-center">
      <FileKindIcon kind={kind} className="size-12 text-muted-foreground" />
      <div className="max-w-full space-y-1">
        <p className="text-sm font-medium break-all">{name}</p>
        <p className="text-sm text-muted-foreground">{size}</p>
      </div>
      <a
        href={file.url}
        target="_blank"
        rel="noreferrer"
        className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
      >
        <ExternalLinkIcon aria-hidden />
        {t("attachments.openInNewTab")}
      </a>
    </div>
  );
}
