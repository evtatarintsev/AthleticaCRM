import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, InfoIcon, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type { ClientId, UploadId } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Avatar } from "@/ui/Avatar";
import { BarsChart, type BarPoint } from "./charts/BarsChart";
import { groupColor } from "./charts/colors";
import { ReportChangeBadge } from "./ReportChangeBadge";
import { ReportFilters } from "./ReportFilters";
import type { ChangeTone, ReportChangeView } from "./reportFormat";
import type { ReportSearch } from "./reportSearch";
import { ReportIcon } from "./ReportStubCard";
import type { ReportDefinition } from "./registry";

/** Главная цифра экрана отчёта на цветной плашке с графиком за последние периоды. */
export interface ReportHero {
  /** Главная цифра, готовая строка. */
  readonly value: string;
  /** Подпись под цифрой («за 1–27 ноября»). */
  readonly caption: string;
  /** Изменение к прошлому периоду; `null` — прошлого периода нет. */
  readonly change: ReportChangeView | null;
  /** Столбцы графика за последние периоды. */
  readonly chart: readonly BarPoint[];
  /** Текстовый пересказ графика. */
  readonly chartLabel: string;
}

/** Плитка показателя под главной цифрой. */
export interface ReportTileProps {
  readonly icon: LucideIcon;
  readonly label: string;
  readonly value: string;
  /** Пояснение под значением; цвет задаёт [tone]. */
  readonly note?: string;
  readonly tone?: ChangeTone;
}

/** Плитка показателя: иконка, подпись, значение и пояснение. */
export function ReportTile({ icon: Icon, label, value, note, tone = "neutral" }: ReportTileProps) {
  return (
    <div className="bg-card flex items-center gap-3 rounded-xl border p-4">
      <span
        aria-hidden="true"
        className="bg-muted flex size-11 shrink-0 items-center justify-center rounded-lg"
      >
        <Icon className="size-5" />
      </span>
      <div className="min-w-0">
        <p className="text-muted-foreground text-sm">{label}</p>
        <p className="text-xl font-bold tracking-tight">{value}</p>
        {note !== undefined && (
          <p
            data-tone={tone}
            className={cn(
              "text-xs font-medium",
              tone === "good" && "text-green-700 dark:text-green-400",
              tone === "bad" && "text-red-700 dark:text-red-400",
              tone === "neutral" && "text-muted-foreground",
            )}
          >
            {note}
          </p>
        )}
      </div>
    </div>
  );
}

/** Разбивка отчёта: карточка с заголовком [title], действиями [actions] справа и содержимым. */
export function ReportSection({
  title,
  actions,
  children,
}: {
  readonly title: string;
  readonly actions?: ReactNode;
  readonly children: ReactNode;
}) {
  return (
    <section className="bg-card space-y-4 rounded-xl border p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-semibold">{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  );
}

/** Человек в списке для действия: клиент со ссылкой на карточку. */
export interface ReportPerson {
  readonly clientId: ClientId;
  readonly name: string;
  /** Аватар клиента; `null` — инициалы. */
  readonly avatarId: UploadId | null;
  /** Подпись под именем («должен 34 дня»). */
  readonly caption: string;
  /** Значение справа («12 400 ₽»). */
  readonly value?: string;
}

/** Список людей для действия с переходом на карточку клиента. */
export function ReportPeople({
  api,
  title,
  people,
}: {
  readonly api: ApiClient;
  readonly title: string;
  readonly people: readonly ReportPerson[];
}) {
  return (
    <ReportSection title={title}>
      <ul className="divide-y">
        {people.map((person) => (
          <li key={person.clientId} className="flex items-center gap-3 py-2.5">
            <Avatar api={api} uploadId={person.avatarId} name={person.name} className="size-9" />
            <div className="min-w-0 flex-1">
              <Link
                to="/clients/$clientId"
                params={{ clientId: person.clientId }}
                className="font-medium hover:underline"
              >
                {person.name}
              </Link>
              <p className="text-muted-foreground text-sm">{person.caption}</p>
            </div>
            {person.value !== undefined && (
              <span className="font-semibold whitespace-nowrap">{person.value}</span>
            )}
          </li>
        ))}
      </ul>
    </ReportSection>
  );
}

/**
 * Экран отчёта [report]: «← Все отчёты» с теми же фильтрами, заголовок и фильтры, цветная
 * плашка [hero], три плитки [tiles], разбивки и список людей [children], строка «Как считается».
 */
export function ReportScreen({
  api,
  report,
  search,
  onSearchChange,
  hero,
  tiles,
  howCalculated,
  children,
}: {
  readonly api: ApiClient;
  readonly report: ReportDefinition;
  readonly search: ReportSearch;
  readonly onSearchChange: (search: ReportSearch) => void;
  readonly hero: ReportHero;
  readonly tiles: readonly ReportTileProps[];
  readonly howCalculated: string;
  readonly children?: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <section className="max-w-6xl min-w-0 space-y-5">
      <Link
        to="/reports"
        search={search}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1.5 text-sm"
      >
        <ArrowLeftIcon aria-hidden className="size-4" />
        {t("reports.allReports")}
      </Link>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex items-center gap-3">
          <ReportIcon report={report} size="lg" />
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">
              {t(`reports.${report.id}.name`)}
            </h1>
            <p className="text-muted-foreground text-sm">{t(`reports.${report.id}.question`)}</p>
          </div>
        </div>
        <ReportFilters api={api} search={search} onSearchChange={onSearchChange} />
      </div>
      <div
        className="grid gap-4 rounded-2xl p-5 text-white md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]"
        style={{ background: groupColor(report.group) }}
      >
        <div className="flex flex-col justify-center gap-2">
          <span className="text-sm opacity-80">{hero.caption}</span>
          <span className="text-4xl font-bold tracking-tight">{hero.value}</span>
          {hero.change !== null && (
            <span className="self-start">
              <ReportChangeBadge change={hero.change} />
            </span>
          )}
        </div>
        <div className="rounded-xl bg-white/95 p-3 text-slate-700">
          <BarsChart
            data={hero.chart}
            ariaLabel={hero.chartLabel}
            color={() => groupColor(report.group)}
          />
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        {tiles.map((tile) => (
          <ReportTile key={tile.label} {...tile} />
        ))}
      </div>
      {children}
      <p className="text-muted-foreground flex items-start gap-2 text-sm">
        <InfoIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
        <span>
          <span className="font-medium">{t("reports.howCalculated")}: </span>
          {howCalculated}
        </span>
      </p>
    </section>
  );
}
