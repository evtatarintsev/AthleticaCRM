import type { TestingLibraryMatchers } from "@testing-library/jest-dom/matchers";

/**
 * Матчеры jest-dom для `expect` из vitest.
 *
 * Собственные типы `@testing-library/jest-dom/vitest` объявляют `Assertion` с одним
 * параметром типа, а vitest 5 — с двумя; при `skipLibCheck: false` это ошибка TS2428.
 * Поэтому матчеры регистрируются в `test-setup.ts` через `expect.extend`, а типы
 * подключаются здесь в форме, совместимой с vitest.
 */
declare module "vitest" {
  interface Assertion<R, T> extends TestingLibraryMatchers<unknown, R> {}
}
