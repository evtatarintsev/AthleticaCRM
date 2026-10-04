import { z } from "zod";

/** Панели, открываемые поверх страницы настроек. */
export const SettingsPanelSchema = z.enum([
  "edit-profile",
  "switch-branch",
  "change-password",
  "basic",
  "org-balance",
  "branches",
  "disciplines",
  "halls",
  "roles",
  "client-sources",
  "client-additional-attributes",
  "client-import",
]);

/** Панель страницы настроек. */
export type SettingsPanel = z.output<typeof SettingsPanelSchema>;

/**
 * Search-параметры адреса `/settings`: открытая панель. Некорректное значение
 * сбрасывается, а не роняет страницу.
 */
export const SettingsSearchSchema = z.object({
  panel: SettingsPanelSchema.optional().catch(undefined),
});

/** Search-параметры адреса страницы настроек. */
export type SettingsSearch = z.output<typeof SettingsSearchSchema>;
