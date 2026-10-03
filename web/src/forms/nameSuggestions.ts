/** Подсказка названия записи справочника. */
export interface Suggestion {
  /** Стабильный ключ подсказки. */
  readonly key: string;
  /** Название, которое подставляется в поле. */
  readonly label: string;
  /** Написания, по которым подсказка ищется; первое — [label]. */
  readonly terms: readonly string[];
}

/** Группа подсказок с подписью [label]. */
export interface SuggestionGroup {
  /** Стабильный ключ группы. */
  readonly key: string;
  /** Подпись группы. */
  readonly label: string;
  /** Подсказки группы в порядке показа. */
  readonly items: readonly Suggestion[];
}

/** Подсказка, подошедшая под ввод. */
export interface MatchedSuggestion {
  /** Сама подсказка. */
  readonly suggestion: Suggestion;
  /** Сработавшее написание, если подсказка подошла не по своему названию; иначе `null`. */
  readonly hint: string | null;
}

/** Группа подошедших подсказок. */
export interface MatchedGroup {
  /** Исходная группа. */
  readonly group: SuggestionGroup;
  /** Подошедшие подсказки группы в порядке показа. */
  readonly items: readonly MatchedSuggestion[];
}

/**
 * Строка для сравнения: без пробелов по краям, в нижнем регистре, «ё» как «е»,
 * дефисы и подчёркивания как пробелы, несколько пробелов подряд как один.
 */
export function normalize(value: string): string {
  return value
    .toLocaleLowerCase()
    .replaceAll("ё", "е")
    .replace(/[-_]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Подсказки из [groups], подходящие под ввод [input]: нормализованный ввод — подстрока
 * любого написания. Группы без подошедших подсказок пропускаются, порядок сохраняется.
 * Пустой ввод оставляет все подсказки.
 */
export function filterSuggestions(
  groups: readonly SuggestionGroup[],
  input: string,
): readonly MatchedGroup[] {
  const query = normalize(input);
  return groups
    .map((group) => ({
      group,
      items: group.items.flatMap((suggestion) => {
        const match = matchOf(suggestion, query);
        return match === undefined ? [] : [match];
      }),
    }))
    .filter((matched) => matched.items.length > 0);
}

/** Совпадение [suggestion] с нормализованным вводом [query] или `undefined`, если не подходит. */
function matchOf(suggestion: Suggestion, query: string): MatchedSuggestion | undefined {
  if (query === "" || normalize(suggestion.label).includes(query)) {
    return { suggestion, hint: null };
  }
  const term = suggestion.terms.find((t) => normalize(t).includes(query));
  return term === undefined ? undefined : { suggestion, hint: term };
}

/**
 * Подсказки из [groups], уже имеющиеся среди названий [names]: ключ подсказки →
 * название, с которым она уже добавлена. Совпадение — равенство после [normalize]
 * с любым написанием подсказки.
 */
export function markExisting(
  groups: readonly SuggestionGroup[],
  names: readonly string[],
): ReadonlyMap<string, string> {
  const byNormalized = new Map(names.map((name) => [normalize(name), name]));
  const existing = new Map<string, string>();
  groups.forEach((group) => {
    group.items.forEach((suggestion) => {
      const name = suggestion.terms
        .map((term) => byNormalized.get(normalize(term)))
        .find((found) => found !== undefined);
      if (name !== undefined) {
        existing.set(suggestion.key, name);
      }
    });
  });
  return existing;
}
