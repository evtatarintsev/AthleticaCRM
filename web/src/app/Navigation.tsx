import { Link, useLocation } from "@tanstack/react-router";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { isStaticAppPath } from "./router";
import { sectionOf, sections, type Section, type SectionId } from "./sections";

/** Больше этого числа бейдж раздела показывает «99+». */
const BADGE_LIMIT = 99;

/**
 * Основная навигация по разделам; каждый пункт открывается ссылкой роутера.
 * [onNavigate] вызывается после выбора пункта, чтобы закрыть выезжающее меню.
 * [badges] — числа у пунктов разделов (например, непрочитанное во «Входящих»); ноль не показывается.
 */
export function Navigation({
  onNavigate,
  badges = {},
}: {
  onNavigate?: () => void;
  badges?: Partial<Record<SectionId, number>>;
}) {
  const { t } = useI18n();
  const current = sectionOf(useLocation({ select: (location) => location.pathname }));
  return (
    <nav aria-label={t("nav.label")}>
      <ul className="space-y-1">
        {sections.map((section) => (
          <li key={section.id}>
            <SectionLink
              section={section}
              current={section.id === current}
              badge={badges[section.id] ?? 0}
              {...(onNavigate === undefined ? {} : { onNavigate })}
            />
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** Пункт навигации раздела [section]; [current] — раздел открыт сейчас, [badge] — число у пункта. */
function SectionLink({
  section,
  current,
  badge,
  onNavigate,
}: {
  section: Section;
  current: boolean;
  badge: number;
  onNavigate?: () => void;
}) {
  const { t } = useI18n();
  const Icon = section.icon;
  const className = cn(
    "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50",
    current && "bg-accent text-accent-foreground",
  );
  const content = (
    <>
      <Icon aria-hidden className="size-4 shrink-0" />
      <span className="min-w-0 flex-1 truncate">{t(section.label)}</span>
      {badge > 0 && (
        <span
          aria-label={t("inbox.navBadge", { count: badge })}
          className="shrink-0 rounded-full bg-primary px-1.5 text-xs font-medium text-primary-foreground"
        >
          {badge > BADGE_LIMIT ? `${String(BADGE_LIMIT)}+` : badge}
        </span>
      )}
    </>
  );
  if (!isStaticAppPath(section.path)) {
    throw new Error(`раздел без маршрута веб-клиента: ${section.path}`);
  }
  return (
    <Link
      to={section.path}
      aria-current={current ? "page" : undefined}
      className={className}
      onClick={onNavigate}
    >
      {content}
    </Link>
  );
}
