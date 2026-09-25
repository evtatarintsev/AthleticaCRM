import type { ApiClient, ApiResult, ResponseOf } from "./client";

/** Загружает файл [file] в хранилище: multipart-запрос с полем `file`, как ждёт сервер. */
export function uploadFile(api: ApiClient, file: File): Promise<ApiResult<ResponseOf<"upload">>> {
  const form = new FormData();
  form.set("file", file, file.name);
  return api.call("upload", form);
}
