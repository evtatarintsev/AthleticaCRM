import { z } from "zod";
import {
  ClientIdSchema,
  OrgIdSchema,
  type ClientAppProfileSchema,
} from "../api/generated/contracts";

/** Ключ `localStorage` выбранного профиля. */
const STORAGE_KEY = "athletica.cabinet.profile";

/** Сохранённый выбор профиля. */
const StoredProfileSchema = z.object({ orgId: OrgIdSchema, clientId: ClientIdSchema }).readonly();

/** Выбранный профиль. */
export type ProfileKey = z.output<typeof StoredProfileSchema>;

/** Профиль [selection] из [profiles], если он ещё подключён, иначе первый; `null` — профилей нет. */
export function selectedProfile(
  profiles: readonly ClientAppProfileSchema[],
  selection: ProfileKey | null,
): ClientAppProfileSchema | null {
  return (
    profiles.find((it) => it.orgId === selection?.orgId && it.clientId === selection.clientId) ??
    profiles[0] ??
    null
  );
}

/** Запоминает на устройстве выбор профиля [profile]; недоступное хранилище не мешает выбору. */
export function storeProfile(profile: ProfileKey): void {
  try {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ orgId: profile.orgId, clientId: profile.clientId }),
    );
  } catch {
    return;
  }
}

/** Сохранённый выбор профиля или `null`, если его нет, он испорчен или хранилище недоступно. */
export function storedProfile(): ProfileKey | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return null;
    }
    const parsed = StoredProfileSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}
