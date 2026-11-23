import { useI18n } from "@/i18n/context";

/** Баннер демо-режима: организация заполнена демонстрационными данными. */
export function DemoBanner() {
  const { t } = useI18n();
  return (
    <section
      aria-label={t("demo.bannerTitle")}
      className="border-b bg-muted/50 px-4 py-3 text-sm lg:px-8"
    >
      <p className="break-words">{t("demo.banner")}</p>
    </section>
  );
}
