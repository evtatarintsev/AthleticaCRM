/** Предел размера одного файла — как на сервере. */
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

/** Предел числа фото в одном сообщении — как на сервере. */
export const MAX_PHOTOS = 10;

/** Типы, которые предлагает выбор файла; камера на мобильном доступна благодаря `image/*`-типам. */
export const ATTACHMENT_ACCEPT =
  ".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,image/jpeg,image/png,image/webp";

/** Причина, по которой набор вложений нельзя отправить. */
export type AttachmentProblem = "unsupported" | "tooBig" | "mixed" | "tooManyPhotos";

/** Вид файла по имени и MIME-типу; окончательно тип по содержимому определяет сервер. */
function kind(file: File): "table" | "image" | null {
  const name = file.name.toLowerCase();
  if (name.endsWith(".csv") || name.endsWith(".xlsx")) {
    return "table";
  }
  if (["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    return "image";
  }
  return null;
}

/**
 * Проверяет набор [files] до отправки: одна таблица csv/xlsx или до [MAX_PHOTOS] изображений,
 * каждый файл до [MAX_ATTACHMENT_BYTES]. `null` — набор допустим.
 */
export function attachmentProblem(files: readonly File[]): AttachmentProblem | null {
  const kinds = files.map(kind);
  if (kinds.some((k) => k === null)) {
    return "unsupported";
  }
  if (files.some((f) => f.size > MAX_ATTACHMENT_BYTES)) {
    return "tooBig";
  }
  const images = kinds.filter((k) => k === "image").length;
  if ((images > 0 && images < files.length) || (images === 0 && files.length > 1)) {
    return "mixed";
  }
  if (images > MAX_PHOTOS) {
    return "tooManyPhotos";
  }
  return null;
}
