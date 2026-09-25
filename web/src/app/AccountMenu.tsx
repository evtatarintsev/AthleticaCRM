import { ChevronDownIcon, LogOutIcon } from "lucide-react";
import type { AuthMeResponse } from "@/api/generated/contracts";
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

/**
 * Меню аккаунта пользователя [me]: профиль, смена филиала, язык и выход ([onLogout]).
 * Профиль и смена филиала пока открываются в KMP-клиенте.
 */
export function AccountMenu({ me, onLogout }: { me: AuthMeResponse; onLogout: () => void }) {
  const { t, locale, setLocale } = useI18n();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="max-w-56 gap-2 px-2" aria-label={t("account.menu")}>
          <span
            aria-hidden
            className="grid size-7 shrink-0 place-items-center rounded-full bg-primary text-xs font-semibold text-primary-foreground"
          >
            {initials(me.name)}
          </span>
          <span className="hidden truncate sm:inline">{me.name}</span>
          <ChevronDownIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuLabel className="space-y-0.5">
          <div className="truncate">{me.name}</div>
          <div className="truncate text-xs font-normal text-muted-foreground">
            {t("account.branch", { name: me.currentBranch.name })}
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <a href="/settings/edit-profile">{t("account.profile")}</a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <a href="/settings">{t("account.switchBranch")}</a>
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
  );
}

/** Инициалы для аватара: первые буквы двух первых слов имени [name]. */
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter((word) => word !== "")
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join("");
}
