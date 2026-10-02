import { Link } from "@tanstack/react-router";
import {
  BookOpenIcon,
  Building2Icon,
  ChevronRightIcon,
  ClipboardCheckIcon,
  DoorOpenIcon,
  FileUpIcon,
  HistoryIcon,
  KeyRoundIcon,
  LandmarkIcon,
  ListPlusIcon,
  MegaphoneIcon,
  MessagesSquareIcon,
  PanelRightOpenIcon,
  SettingsIcon,
  ShieldCheckIcon,
  StoreIcon,
  TableIcon,
  TicketIcon,
  TrophyIcon,
  UserIcon,
  WalletIcon,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import { ChangePasswordSheet } from "@/account/ChangePasswordSheet";
import { ProfileSheet } from "@/account/ProfileSheet";
import { SwitchBranchSheet } from "@/account/SwitchBranchSheet";
import type { StaticAppPath } from "@/app/router";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import type { SettingsPanel } from "./settingsSearch";

/** Куда ведёт пункт настроек. */
type SettingTarget =
  /** Страница веб-клиента. */
  | { readonly kind: "app"; readonly to: StaticAppPath }
  /** Панель поверх страницы настроек. */
  | { readonly kind: "panel"; readonly panel: SettingsPanel }
  /** Пункт-заготовка: экрана нет ни в веб-, ни в KMP-клиенте. */
  | { readonly kind: "none" };

/** Пункт настроек. */
interface SettingItem {
  readonly title: PlainMessageKey;
  readonly subtitle: PlainMessageKey;
  readonly icon: LucideIcon;
  readonly target: SettingTarget;
}

/** Раздел настроек с заголовком [label]. */
interface SettingSection {
  readonly label: PlainMessageKey;
  readonly items: readonly SettingItem[];
}

/** Страница веб-клиента [to]. */
const app = (to: StaticAppPath): SettingTarget => ({ kind: "app", to });

/** Панель [panel] поверх страницы настроек. */
const panel = (panel: SettingsPanel): SettingTarget => ({ kind: "panel", panel });

/** Пункт без экрана. */
const none: SettingTarget = { kind: "none" };

/** Разделы и пункты в том же порядке, что в KMP-клиенте. */
const SETTINGS: readonly SettingSection[] = [
  {
    label: "settings.sectionUser",
    items: [
      {
        title: "settings.itemEditProfile",
        subtitle: "settings.itemEditProfileSubtitle",
        icon: UserIcon,
        target: panel("edit-profile"),
      },
      {
        title: "settings.itemSwitchBranch",
        subtitle: "settings.itemSwitchBranchSubtitle",
        icon: StoreIcon,
        target: panel("switch-branch"),
      },
      {
        title: "settings.itemChangePassword",
        subtitle: "settings.itemChangePasswordSubtitle",
        icon: KeyRoundIcon,
        target: panel("change-password"),
      },
    ],
  },
  {
    label: "settings.sectionBasic",
    items: [
      {
        title: "settings.itemBasicSettings",
        subtitle: "settings.itemBasicSettingsSubtitle",
        icon: SettingsIcon,
        target: app("/settings/basic"),
      },
      {
        title: "settings.itemOrgBalance",
        subtitle: "settings.itemOrgBalanceSubtitle",
        icon: WalletIcon,
        target: app("/settings/org-balance"),
      },
      {
        title: "settings.itemBranches",
        subtitle: "settings.itemBranchesSubtitle",
        icon: Building2Icon,
        target: app("/settings/branches"),
      },
      {
        title: "settings.itemDisciplines",
        subtitle: "settings.itemDisciplinesSubtitle",
        icon: TrophyIcon,
        target: app("/settings/disciplines"),
      },
      {
        title: "settings.itemHalls",
        subtitle: "settings.itemHallsSubtitle",
        icon: DoorOpenIcon,
        target: app("/settings/halls"),
      },
      {
        title: "settings.itemRanks",
        subtitle: "settings.itemRanksSubtitle",
        icon: BookOpenIcon,
        target: none,
      },
    ],
  },
  {
    label: "settings.sectionStaff",
    items: [
      {
        title: "settings.itemActivityLog",
        subtitle: "settings.itemActivityLogSubtitle",
        icon: HistoryIcon,
        target: app("/settings/activity-log"),
      },
      {
        title: "settings.itemRoles",
        subtitle: "settings.itemRolesSubtitle",
        icon: ShieldCheckIcon,
        target: app("/settings/roles"),
      },
    ],
  },
  {
    label: "settings.sectionClients",
    items: [
      {
        title: "settings.itemClientDisplay",
        subtitle: "settings.itemClientDisplaySubtitle",
        icon: TableIcon,
        target: none,
      },
      {
        title: "settings.itemClientSources",
        subtitle: "settings.itemClientSourcesSubtitle",
        icon: MegaphoneIcon,
        target: app("/settings/client-sources"),
      },
      {
        title: "settings.itemClientAdditionalAttributes",
        subtitle: "settings.itemClientAdditionalAttributesSubtitle",
        icon: ListPlusIcon,
        target: app("/settings/client-additional-attributes"),
      },
      {
        title: "settings.itemClientImport",
        subtitle: "settings.itemClientImportSubtitle",
        icon: FileUpIcon,
        target: app("/settings/client-import"),
      },
    ],
  },
  {
    label: "settings.sectionClasses",
    items: [
      {
        title: "settings.itemAttendance",
        subtitle: "settings.itemAttendanceSubtitle",
        icon: ClipboardCheckIcon,
        target: none,
      },
      {
        title: "settings.itemSubscriptionTemplates",
        subtitle: "settings.itemSubscriptionTemplatesSubtitle",
        icon: TicketIcon,
        target: app("/settings/tariffs"),
      },
    ],
  },
  {
    label: "settings.sectionFinance",
    items: [
      {
        title: "settings.itemCashboxes",
        subtitle: "settings.itemCashboxesSubtitle",
        icon: LandmarkIcon,
        target: none,
      },
    ],
  },
  {
    label: "settings.sectionIntegrations",
    items: [
      {
        title: "settings.itemChannels",
        subtitle: "settings.itemChannelsSubtitle",
        icon: MessagesSquareIcon,
        target: app("/settings/channels"),
      },
    ],
  },
];

/**
 * Настройки: разделы и пункты как в KMP-клиенте. Пункты с готовым экраном — ссылки
 * роутера, заготовки без экрана показаны неактивной строкой. Пункты раздела «Пользователь»
 * открывают панель справа, не уходя со страницы; открытая панель [panel] хранится в адресе,
 * её смена сообщается через [onPanelChange].
 */
export function SettingsPage({
  api,
  panel,
  onPanelChange,
}: {
  api: ApiClient;
  panel: SettingsPanel | undefined;
  onPanelChange: (panel: SettingsPanel | undefined) => void;
}) {
  const { t } = useI18n();
  const panelProps = (target: SettingsPanel) => ({
    api,
    open: panel === target,
    onOpenChange: (open: boolean) => {
      onPanelChange(open ? target : undefined);
    },
  });
  return (
    <section className="max-w-3xl space-y-8">
      <h1 className="text-2xl font-semibold tracking-tight">{t("settings.title")}</h1>
      {SETTINGS.map((section) => (
        <section key={section.label} className="space-y-2">
          <h2 className="px-1 text-sm font-medium text-muted-foreground">{t(section.label)}</h2>
          <ul className="divide-y rounded-lg border bg-card">
            {section.items.map((item) => (
              <li key={item.title}>
                <SettingRow item={item} />
              </li>
            ))}
          </ul>
        </section>
      ))}
      <ProfileSheet {...panelProps("edit-profile")} />
      <SwitchBranchSheet {...panelProps("switch-branch")} />
      <ChangePasswordSheet {...panelProps("change-password")} />
    </section>
  );
}

/** Строка пункта [item]: ссылка роутера или неактивная строка-заготовка. */
function SettingRow({ item }: { item: SettingItem }) {
  const { t } = useI18n();
  const Icon = item.icon;
  const className =
    "flex items-center gap-3 px-4 py-3 outline-none transition-colors focus-visible:ring-[3px] focus-visible:ring-ring/50";
  const content = (trailing: ReactNode) => (
    <>
      <span className="grid size-9 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
        <Icon aria-hidden className="size-4" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-medium">{t(item.title)}</span>
        <span className="block text-sm text-muted-foreground">{t(item.subtitle)}</span>
      </span>
      {trailing}
    </>
  );
  switch (item.target.kind) {
    case "app":
      return (
        <Link to={item.target.to} className={`${className} hover:bg-accent/50`}>
          {content(<ChevronRightIcon aria-hidden className="size-4 text-muted-foreground" />)}
        </Link>
      );
    case "panel":
      return (
        <Link
          to="/settings"
          search={{ panel: item.target.panel }}
          className={`${className} hover:bg-accent/50`}
        >
          {content(<PanelRightOpenIcon aria-hidden className="size-4 text-muted-foreground" />)}
        </Link>
      );
    case "none":
      return (
        <div className={`${className} opacity-60`}>
          {content(<span className="text-xs text-muted-foreground">{t("settings.soon")}</span>)}
        </div>
      );
  }
}
