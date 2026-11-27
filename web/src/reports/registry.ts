import {
  BuildingIcon,
  CalendarCheckIcon,
  LayersIcon,
  TicketIcon,
  TrendingUpIcon,
  TriangleAlertIcon,
  UserRoundCheckIcon,
  UsersIcon,
  WalletIcon,
  type LucideIcon,
} from "lucide-react";
import type { ComponentType } from "react";
import type { ApiClient } from "@/api/client";
import type { ReportFilter, UserPermission } from "@/api/generated/contracts";
import type { ReportGroup } from "./charts/colors";
import type { ReportSearch } from "./reportSearch";

/** Идентификатор отчёта; он же сегмент адреса `/reports/$reportId` и префикс ключей словаря. */
export type ReportId =
  | "revenue"
  | "pnl"
  | "debts"
  | "sales"
  | "attendance"
  | "groups"
  | "coaches"
  | "directions"
  | "branches";

/** Свойства готовой части отчёта: клиент API, запрос, фильтры адреса и их смена. */
export interface ReportImplProps {
  readonly api: ApiClient;
  readonly filter: ReportFilter;
  readonly search: ReportSearch;
  readonly onSearchChange: (search: ReportSearch) => void;
}

/** Готовая часть отчёта, которую добавляет изменение отчёта. */
export interface ReportImpl {
  /** Карточка виджета: загружает свою сводку и рисует `ReportCard`. */
  readonly Card: ComponentType<ReportImplProps>;
  /** Экран отчёта по шаблону `ReportScreen`. */
  readonly Screen: ComponentType<ReportImplProps>;
}

/** Описание отчёта для страницы раздела. */
export interface ReportDefinition {
  readonly id: ReportId;
  readonly group: ReportGroup;
  readonly icon: LucideIcon;
  /** Права сверх `CAN_VIEW_REPORTS`. */
  readonly requires: readonly UserPermission[];
  /** Готовая часть отчёта; `undefined` — заглушка «скоро». */
  readonly impl?: ReportImpl;
}

/** Группы отчётов в порядке показа. */
export const reportGroups: readonly ReportGroup[] = ["money", "clients", "team", "compare"];

/** Отчёты раздела в порядке макета. */
export const reports: readonly ReportDefinition[] = [
  { id: "revenue", group: "money", icon: WalletIcon, requires: ["CAN_VIEW_FINANCE"] },
  { id: "pnl", group: "money", icon: TrendingUpIcon, requires: ["CAN_VIEW_FINANCE"] },
  { id: "debts", group: "money", icon: TriangleAlertIcon, requires: ["CAN_VIEW_CLIENT_BALANCE"] },
  { id: "sales", group: "clients", icon: TicketIcon, requires: [] },
  { id: "attendance", group: "clients", icon: CalendarCheckIcon, requires: [] },
  { id: "groups", group: "clients", icon: UsersIcon, requires: [] },
  { id: "coaches", group: "team", icon: UserRoundCheckIcon, requires: [] },
  { id: "directions", group: "compare", icon: LayersIcon, requires: [] },
  { id: "branches", group: "compare", icon: BuildingIcon, requires: ["CAN_VIEW_FINANCE"] },
];

/** Отчёты, которые видит сотрудник с правами [permissions]: `CAN_VIEW_REPORTS` и все из `requires`. */
export function visibleReports(
  permissions: readonly UserPermission[],
): readonly ReportDefinition[] {
  if (!permissions.includes("CAN_VIEW_REPORTS")) {
    return [];
  }
  return reports.filter((report) => report.requires.every((p) => permissions.includes(p)));
}

/** Видимые отчёты по группам; группа без видимых отчётов не попадает в список. */
export function visibleGroups(
  permissions: readonly UserPermission[],
): readonly { readonly group: ReportGroup; readonly reports: readonly ReportDefinition[] }[] {
  const visible = visibleReports(permissions);
  return reportGroups
    .map((group) => ({ group, reports: visible.filter((report) => report.group === group) }))
    .filter((entry) => entry.reports.length > 0);
}

/** Отчёт по сегменту адреса [id]; неизвестный — `undefined`. */
export function reportById(id: string): ReportDefinition | undefined {
  return reports.find((report) => report.id === id);
}
