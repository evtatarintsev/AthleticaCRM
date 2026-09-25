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
  /** Путь раздела; совпадает в веб-клиенте (от `BASE_PATH`) и в KMP-клиенте (от `/`). */
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
 * Разделы, перенесённые в веб-клиент целиком. Навигация на неперенесённый раздел
 * ведёт в KMP-клиент обычной ссылкой; включение раздела — изменение одной строки.
 */
export const migratedSections: Readonly<Record<SectionId, boolean>> = {
  home: false,
  schedule: false,
  clients: false,
  groups: false,
  employees: false,
  tasks: false,
  settings: false,
};

/**
 * Раздел, к которому относится путь [pathname] веб-клиента (без `BASE_PATH`):
 * `/clients/…` → `clients`. Главная — только сам `/`.
 */
export function sectionOf(pathname: string): SectionId | null {
  const section = sections.find((s) =>
    s.path === "/" ? pathname === "/" : pathname === s.path || pathname.startsWith(`${s.path}/`),
  );
  return section?.id ?? null;
}
