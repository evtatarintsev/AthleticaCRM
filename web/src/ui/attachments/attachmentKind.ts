/**
 * Как показать вложение: миниатюрой и картинкой (`image`), встроенным документом (`pdf`),
 * плеером (`video`, `audio`) или карточкой файла (`file`).
 */
export type PreviewKind = "image" | "pdf" | "video" | "audio" | "file";

/**
 * Типы изображений, которые браузеры показывают в `<img>`. Список явный, а не `image/*`:
 * HEIC и TIFF браузеры не показывают, они сразу идут карточкой файла.
 */
const IMAGE_TYPES: ReadonlySet<string> = new Set([
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "image/avif",
  "image/svg+xml",
  "image/bmp",
]);

/** Типы видео, которые браузеры воспроизводят в `<video>`. */
const VIDEO_TYPES: ReadonlySet<string> = new Set(["video/mp4", "video/webm", "video/ogg"]);

/**
 * Способ показа вложения с MIME-типом [contentType]. Регистр и параметры после `;`
 * не учитываются: `image/JPEG; charset=x` — изображение.
 */
export function previewKind(contentType: string): PreviewKind {
  const type = (contentType.split(";")[0] ?? "").trim().toLowerCase();
  if (IMAGE_TYPES.has(type)) {
    return "image";
  }
  if (type === "application/pdf") {
    return "pdf";
  }
  if (VIDEO_TYPES.has(type)) {
    return "video";
  }
  if (type.startsWith("audio/")) {
    return "audio";
  }
  return "file";
}
