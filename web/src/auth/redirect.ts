import { z } from "zod";
import { migratedSections } from "@/app/sections";
import { BASE_PATH } from "@/config";

/**
 * Истина, если [path] — путь своего origin: начинается с одного `/`, без `//` и `\`.
 * Абсолютные URL и адреса вида `//evil.example` отбрасываются.
 */
function isSafeRedirect(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.includes("\\");
}

/** Search-параметры страницы входа: некорректный адрес возврата сбрасывается. */
export const LoginSearchSchema = z.object({
  redirect: z.string().refine(isSafeRedirect).optional().catch(undefined),
});

/**
 * Куда перейти после входа: на адрес возврата [redirect] (путь веб-клиента без `BASE_PATH`),
 * иначе на главную веб-клиента, если она перенесена ([homeMigrated]), иначе на главную KMP-клиента.
 */
export function postLoginTarget(
  redirect: string | undefined,
  homeMigrated: boolean = migratedSections.home,
): string {
  if (redirect !== undefined) {
    return `${BASE_PATH}${redirect}`;
  }
  return homeMigrated ? `${BASE_PATH}/` : "/";
}
