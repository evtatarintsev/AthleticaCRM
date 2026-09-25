import { Link, useLocation } from "@tanstack/react-router";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { isStaticAppPath } from "./router";
import { migratedSections, sectionOf, sections, type Section } from "./sections";

/**
 * Основная навигация по разделам. Перенесённый раздел открывается ссылкой роутера,
 * неперенесённый — обычной ссылкой на тот же путь KMP-клиента. [onNavigate] вызывается
 * после выбора пункта, чтобы закрыть выезжающее меню.
 */
export function Navigation({ onNavigate }: { onNavigate?: () => void }) {
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
              {...(onNavigate === undefined ? {} : { onNavigate })}
            />
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** Пункт навигации раздела [section]; [current] — раздел открыт сейчас. */
function SectionLink({
  section,
  current,
  onNavigate,
}: {
  section: Section;
  current: boolean;
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
      <span className="truncate">{t(section.label)}</span>
    </>
  );
  if (migratedSections[section.id] && isStaticAppPath(section.path)) {
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
  return (
    <a href={section.path} aria-current={current ? "page" : undefined} className={className}>
      {content}
    </a>
  );
}
