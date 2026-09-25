import { Link } from "@tanstack/react-router";
import { buttonVariants } from "@/components/ui/button-variants";
import { useI18n } from "@/i18n/context";

/** Страница «не найдено» для неизвестного адреса или некорректного параметра. */
export function NotFoundPage() {
  const { t } = useI18n();
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-5xl font-semibold text-muted-foreground">404</p>
      <h1 className="text-xl font-semibold">{t("notFound.title")}</h1>
      <p className="text-muted-foreground">{t("notFound.text")}</p>
      <Link to="/" className={buttonVariants({ variant: "outline" })}>
        {t("notFound.home")}
      </Link>
    </main>
  );
}
