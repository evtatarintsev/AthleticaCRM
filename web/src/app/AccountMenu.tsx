import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ChevronDownIcon, LogOutIcon, StoreIcon, UserIcon, WalletIcon } from "lucide-react";
import type { ApiClient } from "@/api/client";
import type { AuthMeResponse } from "@/api/generated/contracts";
import { useSwitchBranch } from "@/account/useSwitchBranch";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useI18n } from "@/i18n/context";
import { LocaleSchema } from "@/i18n/locale";
import { myBranchesQuery } from "@/query/queries";
import { Avatar } from "@/ui/Avatar";

/**
 * Меню аккаунта пользователя [me]: профиль, баланс организации, смена филиала, язык и выход
 * ([onLogout]). Баланс организации пока открывается в KMP-клиенте.
 */
export function AccountMenu({
  api,
  me,
  onLogout,
}: {
  api: ApiClient;
  me: AuthMeResponse;
  onLogout: () => void;
}) {
  const { t, format, locale, setLocale } = useI18n();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="max-w-56 gap-2 px-2" aria-label={t("account.menu")}>
          <Avatar api={api} uploadId={me.avatarId} name={me.name} />
          <span className="hidden truncate sm:inline">{me.name}</span>
          <ChevronDownIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuLabel className="space-y-0.5">
          <div className="truncate">{me.name}</div>
          <div className="truncate text-xs font-normal text-muted-foreground">{me.username}</div>
        </DropdownMenuLabel>
        <DropdownMenuItem asChild>
          <Link to="/settings/edit-profile">
            <UserIcon aria-hidden />
            {t("account.profile")}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <a href="/settings" className="flex-col items-start gap-0.5">
            <span className="flex w-full items-center gap-2 truncate">
              <WalletIcon aria-hidden />
              {me.orgInfo.name}
            </span>
            {me.orgInfo.balance !== null && (
              <span className="pl-6 text-xs text-muted-foreground">
                {t("account.orgBalance", { amount: format.money(me.orgInfo.balance) })}
              </span>
            )}
          </a>
        </DropdownMenuItem>
        <BranchSwitcher api={api} me={me} />
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">
          {t("account.language")}
        </DropdownMenuLabel>
        <DropdownMenuRadioGroup
          value={locale}
          onValueChange={(value) => {
            const parsed = LocaleSchema.safeParse(value);
            if (parsed.success) {
              setLocale(parsed.data);
            }
          }}
        >
          {LocaleSchema.options.map((option) => (
            <DropdownMenuRadioItem key={option} value={option} lang={option}>
              {t(`language.${option}`)}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={onLogout}>
          <LogOutIcon aria-hidden />
          {t("account.logout")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Текущий филиал пользователя [me] и подменю выбора другого. Список филиалов загружается
 * при открытии меню аккаунта; если филиал один, подменю не показывается.
 */
function BranchSwitcher({ api, me }: { api: ApiClient; me: AuthMeResponse }) {
  const { t } = useI18n();
  const { data } = useQuery(myBranchesQuery(api));
  const switching = useSwitchBranch(api);
  const current = (
    <span className="flex min-w-0 flex-col">
      <span className="text-xs text-muted-foreground">{t("account.currentBranch")}</span>
      <span className="truncate">{me.currentBranch.name}</span>
    </span>
  );
  if (data === undefined || data.branches.length < 2) {
    return (
      <DropdownMenuLabel className="flex items-center gap-2 font-normal">
        <StoreIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
        {current}
      </DropdownMenuLabel>
    );
  }
  return (
    <DropdownMenuSub>
      <DropdownMenuSubTrigger
        className="gap-2"
        aria-label={`${t("account.switchBranch")}: ${me.currentBranch.name}`}
        disabled={switching.isPending}
      >
        <StoreIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
        {current}
      </DropdownMenuSubTrigger>
      <DropdownMenuSubContent className="max-w-64">
        <DropdownMenuRadioGroup
          value={me.currentBranch.id}
          onValueChange={(value) => {
            const branch = data.branches.find((b) => b.id === value);
            if (branch !== undefined && branch.id !== me.currentBranch.id) {
              switching.mutate(branch);
            }
          }}
        >
          {data.branches.map((branch) => (
            <DropdownMenuRadioItem key={branch.id} value={branch.id}>
              <span className="truncate">{branch.name}</span>
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuSubContent>
    </DropdownMenuSub>
  );
}
