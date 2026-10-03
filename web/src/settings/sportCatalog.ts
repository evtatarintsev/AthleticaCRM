import type { SportCatalogNamesSchema, SportCatalogResponse } from "@/api/generated/contracts";
import type { Locale } from "@/i18n/locale";
import { LocaleSchema } from "@/i18n/locale";
import type { SuggestionGroup } from "@/forms/nameSuggestions";

/** Название на языке [locale] и следом названия на остальных языках. */
function namesFirst(names: SportCatalogNamesSchema, locale: Locale): readonly string[] {
  return [names[locale], ...LocaleSchema.options.filter((l) => l !== locale).map((l) => names[l])];
}

/**
 * Каталог видов спорта [catalog] как группы подсказок названия дисциплины: группа — вид спорта,
 * подписи — на языке интерфейса [locale]; дисциплина ищется по названиям на всех языках и синонимам.
 */
export function catalogSuggestions(
  catalog: SportCatalogResponse,
  locale: Locale,
): readonly SuggestionGroup[] {
  return catalog.sports.map((sport) => ({
    key: sport.key,
    label: sport.names[locale],
    items: sport.disciplines.map((discipline) => ({
      key: discipline.key,
      label: discipline.names[locale],
      terms: [...namesFirst(discipline.names, locale), ...discipline.aliases],
    })),
  }));
}
