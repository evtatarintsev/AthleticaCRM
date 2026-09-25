/** Часовой пояс браузера, например `Europe/Moscow`. */
export function browserTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
}

/** Список IANA-поясов для выбора; всегда содержит [current]. */
export function availableTimezones(current: string): string[] {
  const zones = Intl.supportedValuesOf("timeZone");
  return zones.includes(current) ? zones : [current, ...zones];
}
