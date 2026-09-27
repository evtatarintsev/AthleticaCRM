import {
  CalendarDaysIcon,
  CheckSquareIcon,
  HomeIcon,
  IdCardIcon,
  SettingsIcon,
  UsersIcon,
  UsersRoundIcon,
  type LucideIcon,
} from "lucide-react";
import type { ru } from "@/i18n/ru";

/** Раздел приложения в основной навигации. */
export type SectionId =
  "home" | "schedule" | "clients" | "groups" | "employees" | "tasks" | "settings";

/** Пункт основной навигации. */
export interface Section {
  /** Идентификатор раздела. */
  readonly id: SectionId;
  /** Путь раздела в веб-клиенте. */
  readonly path: string;
  /** Ключ подписи в словаре. */
  readonly label: keyof typeof ru;
  /** Иконка пункта. */
  readonly icon: LucideIcon;
}

/** Разделы в порядке показа в навигации. */
export const sections: readonly Section[] = [
  { id: "home", path: "/", label: "nav.home", icon: HomeIcon },
  { id: "schedule", path: "/schedule", label: "nav.schedule", icon: CalendarDaysIcon },
  { id: "clients", path: "/clients", label: "nav.clients", icon: UsersIcon },
  { id: "groups", path: "/groups", label: "nav.groups", icon: UsersRoundIcon },
  { id: "employees", path: "/employees", label: "nav.employees", icon: IdCardIcon },
  { id: "tasks", path: "/tasks", label: "nav.tasks", icon: CheckSquareIcon },
  { id: "settings", path: "/settings", label: "nav.settings", icon: SettingsIcon },
];

/**
 * Раздел, к которому относится путь [pathname] веб-клиента:
 * `/clients/…` → `clients`. Главная — только сам `/`.
 */
export function sectionOf(pathname: string): SectionId | null {
  const section = sections.find((s) =>
    s.path === "/" ? pathname === "/" : pathname === s.path || pathname.startsWith(`${s.path}/`),
  );
  return section?.id ?? null;
}
