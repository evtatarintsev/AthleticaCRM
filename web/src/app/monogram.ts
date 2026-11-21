/**
 * Монограмма клуба по названию организации [name]: заглавные первые буквы первых двух слов,
 * для названия из одного слова — одна буква. Слова делятся пробелами и дефисами, кавычки и
 * другие знаки вокруг слова не считаются буквами.
 */
export function monogramOf(name: string): string {
  return name
    .split(/[\s\-‐-—]+/u)
    .map((word) => word.replace(/[^\p{L}\p{N}]/gu, ""))
    .filter((word) => word !== "")
    .slice(0, 2)
    .map((word) => /^./u.exec(word)?.[0].toLocaleUpperCase() ?? "")
    .join("");
}
