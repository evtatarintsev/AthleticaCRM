import type { SessionColorKey } from "@/api/generated/contracts";

/** Классы Tailwind карточки занятия по ключу палитры сервера. */
export interface ScheduleCardColors {
  /** Светлый фон карточки. */
  readonly tint: string;
  /** Цвет времени занятия на карточке. */
  readonly ink: string;
  /** Фон полосы слева от карточки и строки и отметки дня в полосе дней. */
  readonly bar: string;
}

/** Нейтральный цвет для неизвестного клиенту ключа палитры. */
const NEUTRAL: ScheduleCardColors = {
  tint: "bg-muted",
  ink: "text-foreground",
  bar: "bg-muted-foreground",
};

/**
 * Цвет карточки по ключу палитры [key], который присылает сервер (см. `schedule` — «Цвет
 * карточки определяется сервером»). Неизвестный ключ получает нейтральный цвет.
 */
export function scheduleCardColors(key: SessionColorKey): ScheduleCardColors {
  switch (key) {
    case "ORANGE":
      return {
        tint: "bg-orange-100/80 dark:bg-orange-950/70",
        ink: "text-orange-800 dark:text-orange-300",
        bar: "bg-orange-500 dark:bg-orange-400",
      };
    case "PURPLE":
      return {
        tint: "bg-purple-100/80 dark:bg-purple-950/70",
        ink: "text-purple-800 dark:text-purple-300",
        bar: "bg-purple-600 dark:bg-purple-400",
      };
    case "STEEL":
      return {
        tint: "bg-slate-100 dark:bg-slate-900",
        ink: "text-slate-700 dark:text-slate-300",
        bar: "bg-slate-500 dark:bg-slate-400",
      };
    case "CYAN":
      return {
        tint: "bg-cyan-50 dark:bg-cyan-950/70",
        ink: "text-cyan-700 dark:text-cyan-300",
        bar: "bg-cyan-600 dark:bg-cyan-400",
      };
    case "GREEN":
      return {
        tint: "bg-green-50 dark:bg-green-950/70",
        ink: "text-green-800 dark:text-green-300",
        bar: "bg-green-600 dark:bg-green-400",
      };
    case "LIME":
      return {
        tint: "bg-lime-50 dark:bg-lime-950/70",
        ink: "text-lime-800 dark:text-lime-300",
        bar: "bg-lime-600 dark:bg-lime-400",
      };
    case "LILAC":
      return {
        tint: "bg-violet-100/80 dark:bg-violet-950/70",
        ink: "text-violet-800 dark:text-violet-300",
        bar: "bg-violet-500 dark:bg-violet-400",
      };
    case "GREY":
      return {
        tint: "bg-gray-100 dark:bg-gray-900",
        ink: "text-gray-700 dark:text-gray-300",
        bar: "bg-gray-400 dark:bg-gray-500",
      };
    case "UNKNOWN":
      return NEUTRAL;
  }
}
