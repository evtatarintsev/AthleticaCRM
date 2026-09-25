import { useQueryClient } from "@tanstack/react-query";
import { CameraIcon } from "lucide-react";
import { useId, useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import type { UploadId } from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { uploadInfoQuery } from "@/query/queries";
import { Avatar } from "@/ui/Avatar";

/** Свойства выбора аватара. */
interface AvatarPickerProps {
  /** Клиент API для загрузки файла. */
  readonly api: ApiClient;
  /** Текущая картинка; `null` — картинки нет. */
  readonly value: UploadId | null;
  /** Имя владельца для инициалов. */
  readonly name: string;
  /** Вызывается с идентификатором загруженной картинки. */
  readonly onChange: (id: UploadId) => void;
  /** Выбор недоступен, например пока сохраняется форма. */
  readonly disabled: boolean;
}

/**
 * Аватар с кнопкой выбора картинки. Выбранный файл сразу загружается на сервер, наружу
 * отдаётся идентификатор загрузки; ссылка из ответа кладётся в кэш, чтобы превью появилось сразу.
 */
export function AvatarPicker({ api, value, name, onChange, disabled }: AvatarPickerProps) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const errorId = useId();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = async (file: File) => {
    if (!file.type.startsWith("image/")) {
      setError(t("profile.photoNotImage"));
      return;
    }
    setUploading(true);
    setError(null);
    const result = await uploadFile(api, file);
    setUploading(false);
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    queryClient.setQueryData(uploadInfoQuery(api, result.value.id).queryKey, result.value);
    onChange(result.value.id);
  };

  return (
    <div className="flex flex-col items-center gap-2">
      <Avatar api={api} uploadId={value} name={name} className="size-24 text-2xl" />
      <input
        ref={input}
        type="file"
        accept="image/*"
        tabIndex={-1}
        aria-hidden
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.item(0) ?? null;
          event.target.value = "";
          if (file !== null) {
            void upload(file);
          }
        }}
      />
      <Button
        type="button"
        variant="ghost"
        size="sm"
        disabled={disabled || uploading}
        aria-busy={uploading}
        aria-describedby={error === null ? undefined : errorId}
        onClick={() => {
          input.current?.click();
        }}
      >
        <CameraIcon aria-hidden />
        {uploading
          ? t("profile.uploading")
          : value === null
            ? t("profile.addPhoto")
            : t("profile.changePhoto")}
      </Button>
      {error !== null && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
