import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { UploadId } from "@/api/generated/contracts";
import { cn } from "@/lib/utils";
import { uploadInfoQuery } from "@/query/queries";

/** Свойства аватара. */
interface AvatarProps {
  /** Клиент API: по [uploadId] запрашивается подписанная ссылка на картинку. */
  readonly api: ApiClient;
  /** Загруженная картинка; `null` — показываются инициалы. */
  readonly uploadId: UploadId | null;
  /** Имя владельца: из него берутся инициалы. */
  readonly name: string;
  /** Размер и форма; по умолчанию круг 28 px. */
  readonly className?: string;
}

/**
 * Аватар: картинка загрузки [AvatarProps.uploadId] либо инициалы, если картинки нет,
 * ссылка ещё загружается или картинку не удалось показать (например, истекла подпись).
 * Декоративный: подпись даёт окружающий элемент.
 */
export function Avatar({ api, uploadId, name, className }: AvatarProps) {
  const classes = cn(
    "relative grid size-7 shrink-0 place-items-center overflow-hidden rounded-full bg-primary text-xs font-semibold text-primary-foreground",
    className,
  );
  return (
    <span aria-hidden className={classes}>
      {initials(name)}
      {uploadId !== null && <AvatarImage key={uploadId} api={api} uploadId={uploadId} />}
    </span>
  );
}

/** Картинка загрузки [uploadId] поверх инициалов; при ошибке загрузки скрывается. */
function AvatarImage({ api, uploadId }: { api: ApiClient; uploadId: UploadId }) {
  const { data } = useQuery(uploadInfoQuery(api, uploadId));
  const [broken, setBroken] = useState(false);
  if (data === undefined || broken) {
    return null;
  }
  return (
    <img
      src={data.url}
      alt=""
      className="absolute inset-0 size-full object-cover"
      onError={() => {
        setBroken(true);
      }}
    />
  );
}

/** Инициалы: первые буквы двух первых слов имени [name]. */
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter((word) => word !== "")
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join("");
}
