/** Часовой пояс браузера, например `Europe/Moscow`. */
export function browserTimezone(): string {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return zone === "" ? "UTC" : zone;
}

/** Список IANA-поясов для выбора; всегда содержит [current]. */
export function availableTimezones(current: string): string[] {
  const zones = Intl.supportedValuesOf("timeZone");
  return zones.includes(current) ? zones : [current, ...zones];
}
