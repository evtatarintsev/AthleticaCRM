import { useI18n } from "./i18n/context";

/** Корневой экран кабинета клиента; экраны входа и профилей появятся по шагам изменения. */
export function App() {
  const { t } = useI18n();
  return (
    <main>
      <h1>{t("app.title")}</h1>
      <p>{t("app.comingSoon")}</p>
    </main>
  );
}
