import { buttonVariants } from "@/components/ui/button-variants";
import { useI18n } from "@/i18n/context";

/** Главная веб-клиента, пока раздел «Главная» не перенесён: ведёт в основное приложение. */
export function HomePage() {
  const { t } = useI18n();
  return (
    <section className="max-w-prose space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">{t("home.title")}</h1>
      <p className="text-muted-foreground">{t("home.notMigrated")}</p>
      <a href="/" className={buttonVariants()}>
        {t("home.openApp")}
      </a>
    </section>
  );
}
