import { useQuery } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { ChevronDownIcon, LogOutIcon, SparklesIcon, UserIcon, WalletIcon } from "lucide-react";
import type { ApiClient } from "@/api/client";
import type { AuthMeResponse } from "@/api/generated/contracts";
import { ProfileSheet } from "@/account/ProfileSheet";
import { OrgBalanceSheet } from "@/settings/org/OrgBalanceSheet";
import { assistantAllowed, assistantStatusQuery } from "@/assistant/assistantQueries";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useI18n } from "@/i18n/context";
import { LocaleSchema } from "@/i18n/locale";
import { Avatar } from "@/ui/Avatar";

/**
 * Меню аккаунта пользователя [me]: профиль, баланс организации, язык и выход ([onLogout]).
 * Профиль и баланс открываются панелью поверх текущей страницы. На узком экране первым
 * пунктом идёт помощник ([onOpenAssistant]), если он доступен сотруднику. Помощник открывается
 * после того, как меню вернуло фокус на свою кнопку: туда же панель вернёт его при закрытии.
 */
export function AccountMenu({
  api,
  me,
  onLogout,
  onOpenAssistant,
}: {
  api: ApiClient;
  me: AuthMeResponse;
  onLogout: () => void;
  onOpenAssistant: () => void;
}) {
  const { t, format, locale, setLocale } = useI18n();
  const assistant = useQuery(assistantStatusQuery(api, me.employeeId));
  const assistantRequested = useRef(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [balanceOpen, setBalanceOpen] = useState(false);
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" className="max-w-56 gap-2 px-2" aria-label={t("account.menu")}>
            <Avatar api={api} uploadId={me.avatarId} name={me.name} />
            <span className="hidden truncate sm:inline">{me.name}</span>
            <ChevronDownIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          align="end"
          className="w-64"
          onCloseAutoFocus={() => {
            if (assistantRequested.current) {
              assistantRequested.current = false;
              onOpenAssistant();
            }
          }}
        >
          <DropdownMenuLabel className="space-y-0.5">
            <div className="truncate">{me.name}</div>
            <div className="truncate text-xs font-normal text-muted-foreground">{me.username}</div>
          </DropdownMenuLabel>
          {assistantAllowed(assistant.data) && (
            <DropdownMenuItem
              className="lg:hidden"
              onSelect={() => {
                assistantRequested.current = true;
              }}
            >
              <SparklesIcon aria-hidden className="text-primary" />
              {t("assistant.title")}
            </DropdownMenuItem>
          )}
          <DropdownMenuItem
            onSelect={() => {
              setProfileOpen(true);
            }}
          >
            <UserIcon aria-hidden />
            {t("account.profile")}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            className="flex-col items-start gap-0.5"
            onSelect={() => {
              setBalanceOpen(true);
            }}
          >
            <span className="flex w-full items-center gap-2 truncate">
              <WalletIcon aria-hidden />
              {me.orgInfo.name}
            </span>
            {me.orgInfo.balance !== null && (
              <span className="pl-6 text-xs text-muted-foreground">
                {t("account.orgBalance", { amount: format.money(me.orgInfo.balance) })}
              </span>
            )}
          </DropdownMenuItem>
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
      <ProfileSheet api={api} open={profileOpen} onOpenChange={setProfileOpen} />
      <OrgBalanceSheet api={api} open={balanceOpen} onOpenChange={setBalanceOpen} />
    </>
  );
}
