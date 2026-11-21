import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";

/** Логотип продукта на светлой подложке для читаемости в обеих темах. */
export function Logo({ className }: { className: string }) {
  const { t } = useI18n();
  return (
    <img
      src="/logo.png"
      alt={t("app.name")}
      width={1152}
      height={250}
      className={cn("block h-auto rounded-lg bg-white p-2", className)}
    />
  );
}
