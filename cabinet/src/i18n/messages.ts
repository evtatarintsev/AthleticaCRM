/** Формы сообщения с числом по категориям `Intl.PluralRules`; `other` обязательна. */
export interface PluralForms {
  readonly zero?: string;
  readonly one?: string;
  readonly two?: string;
  readonly few?: string;
  readonly many?: string;
  readonly other: string;
}

/** Сообщение словаря: строка с параметрами `{name}` или формы для числа `{count}`. */
export type Message = string | PluralForms;

/** Словарь: ключ сообщения → сообщение. */
export type Dictionary = Readonly<Record<string, Message>>;

/**
 * Словарь перевода с тем же набором ключей, что у словаря-источника [Source].
 * Недостающий или лишний ключ — ошибка компиляции.
 */
export type Messages<Source extends Dictionary> = {
  readonly [K in keyof Source]: Source[K] extends string ? string : PluralForms;
};

/** Имена параметров `{name}` в строке [S]. */
type Placeholders<S> = S extends `${string}{${infer Name}}${infer Rest}`
  ? Name | Placeholders<Rest>
  : never;

/** Значение параметра сообщения; числа форматируются по правилам языка. */
export type ParamValue = string | number;

/** Параметры сообщения [M]: для форм числа обязателен `count`. */
export type ParamsOf<M> = M extends string
  ? Readonly<Record<Placeholders<M>, ParamValue>>
  : M extends PluralForms
    ? Readonly<{ count: number } & Record<Exclude<Placeholders<M[keyof M]>, "count">, ParamValue>>
    : never;

/** Аргументы после ключа: параметры, если в сообщении [M] они есть. */
export type ArgsOf<M> = [keyof ParamsOf<M>] extends [never] ? [] : [params: ParamsOf<M>];

/** Перевод по словарю-источнику [Source]: ключ и типизированные параметры сообщения. */
export type Translate<Source extends Dictionary> = <K extends keyof Source & string>(
  key: K,
  ...args: ArgsOf<Source[K]>
) => string;

/**
 * Функция перевода для языка [locale] по словарю [dictionary].
 * Ключи и параметры проверяются по словарю-источнику [Source].
 */
export function createTranslator<Source extends Dictionary>(
  locale: string,
  dictionary: Messages<Source>,
): Translate<Source> {
  const plurals = new Intl.PluralRules(locale);
  const numbers = new Intl.NumberFormat(locale);
  const messages: Readonly<Record<string, Message>> = dictionary;

  function translate<K extends keyof Source & string>(key: K, ...args: ArgsOf<Source[K]>): string;
  function translate(key: string, ...args: readonly unknown[]): string {
    const message = messages[key];
    const params = args[0];
    if (message === undefined) {
      return key;
    }
    if (typeof message === "string") {
      return interpolate(message, params, numbers);
    }
    const count = isRecord(params) ? params["count"] : undefined;
    const form =
      typeof count === "number" ? (message[plurals.select(count)] ?? message.other) : message.other;
    return interpolate(form, params, numbers);
  }

  return translate;
}

/** Строка [template] с подставленными значениями [params] вместо `{name}`. */
function interpolate(template: string, params: unknown, numbers: Intl.NumberFormat): string {
  return template.replace(/\{(\w+)\}/g, (placeholder, name: string) => {
    const value = isRecord(params) ? params[name] : undefined;
    if (typeof value === "number") {
      return numbers.format(value);
    }
    return typeof value === "string" ? value : placeholder;
  });
}

/** Истина, если [value] — объект со строковыми ключами. */
function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === "object" && value !== null;
}
