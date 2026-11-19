import type { SessionColorKey } from "@/api/generated/contracts";

/** Классы Tailwind для фона, текста и цветной полосы карточки занятия по ключу палитры сервера. */
export interface ScheduleCardColors {
  readonly container: string;
  readonly text: string;
  /** Фон полосы слева от карточки и строки и отметки дня в полосе дней. */
  readonly bar: string;
}

/** Нейтральный цвет для неизвестного клиенту ключа палитры. */
const NEUTRAL: ScheduleCardColors = {
  container: "bg-muted border-border",
  text: "text-foreground",
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
        container: "bg-orange-100 border-orange-300 dark:bg-orange-950 dark:border-orange-800",
        text: "text-orange-900 dark:text-orange-100",
        bar: "bg-orange-500 dark:bg-orange-400",
      };
    case "PURPLE":
      return {
        container: "bg-purple-100 border-purple-300 dark:bg-purple-950 dark:border-purple-800",
        text: "text-purple-900 dark:text-purple-100",
        bar: "bg-purple-500 dark:bg-purple-400",
      };
    case "STEEL":
      return {
        container: "bg-slate-100 border-slate-300 dark:bg-slate-900 dark:border-slate-700",
        text: "text-slate-900 dark:text-slate-100",
        bar: "bg-slate-500 dark:bg-slate-400",
      };
    case "CYAN":
      return {
        container: "bg-cyan-100 border-cyan-300 dark:bg-cyan-950 dark:border-cyan-800",
        text: "text-cyan-900 dark:text-cyan-100",
        bar: "bg-cyan-500 dark:bg-cyan-400",
      };
    case "GREEN":
      return {
        container: "bg-green-100 border-green-300 dark:bg-green-950 dark:border-green-800",
        text: "text-green-900 dark:text-green-100",
        bar: "bg-green-500 dark:bg-green-400",
      };
    case "LIME":
      return {
        container: "bg-lime-100 border-lime-300 dark:bg-lime-950 dark:border-lime-800",
        text: "text-lime-900 dark:text-lime-100",
        bar: "bg-lime-500 dark:bg-lime-400",
      };
    case "LILAC":
      return {
        container: "bg-violet-100 border-violet-300 dark:bg-violet-950 dark:border-violet-800",
        text: "text-violet-900 dark:text-violet-100",
        bar: "bg-violet-500 dark:bg-violet-400",
      };
    case "GREY":
      return {
        container: "bg-gray-100 border-gray-300 dark:bg-gray-900 dark:border-gray-700",
        text: "text-gray-900 dark:text-gray-100",
        bar: "bg-gray-500 dark:bg-gray-400",
      };
    case "UNKNOWN":
      return NEUTRAL;
  }
}
