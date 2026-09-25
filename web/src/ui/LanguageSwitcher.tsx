import { useI18n } from "@/i18n/context";
import { LocaleSchema } from "@/i18n/locale";
import { cn } from "@/lib/utils";

/** Переключатель языка интерфейса кнопками; текущий язык отмечен `aria-pressed`. */
export function LanguageSwitcher() {
  const { t, locale, setLocale } = useI18n();
  return (
    <div role="group" aria-label={t("account.language")} className="flex gap-1 text-sm">
      {LocaleSchema.options.map((option) => (
        <button
          key={option}
          type="button"
          lang={option}
          aria-pressed={option === locale}
          onClick={() => {
            setLocale(option);
          }}
          className={cn(
            "rounded-md px-2 py-1 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50",
            option === locale && "font-medium text-foreground",
          )}
        >
          {t(`language.${option}`)}
        </button>
      ))}
    </div>
  );
}
