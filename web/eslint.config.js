import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";
import tseslint from "typescript-eslint";

/**
 * Максимально строгий анализ веб-клиента (design §2 изменения
 * `rewrite-web-frontend-typescript`).
 *
 * Директивы `eslint-disable` в исходниках не действуют и сами считаются ошибкой:
 * исключение из правил задаётся только здесь, отдельным блоком `files: [...]`
 * с комментарием о причине.
 */
export default tseslint.config(
  { ignores: ["dist", "node_modules"] },
  {
    linterOptions: {
      noInlineConfig: true,
      reportUnusedDisableDirectives: "error",
    },
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.strictTypeChecked,
      ...tseslint.configs.stylisticTypeChecked,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: { "react-hooks": reactHooks, "react-refresh": reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": ["error", { allowConstantExport: true }],
      "@typescript-eslint/consistent-type-assertions": ["error", { assertionStyle: "never" }],
      "@typescript-eslint/ban-ts-comment": [
        "error",
        {
          "ts-check": false,
          "ts-expect-error": true,
          "ts-ignore": true,
          "ts-nocheck": true,
        },
      ],
      "@typescript-eslint/switch-exhaustiveness-check": [
        "error",
        {
          requireDefaultForNonUnion: true,
          considerDefaultExhaustiveForUnions: false,
          allowDefaultCaseForExhaustiveSwitch: false,
        },
      ],
      "@typescript-eslint/strict-boolean-expressions": [
        "error",
        {
          allowString: false,
          allowNumber: false,
          allowNullableObject: false,
          allowNullableBoolean: false,
          allowNullableString: false,
          allowNullableNumber: false,
          allowNullableEnum: false,
          allowAny: false,
        },
      ],
      "@typescript-eslint/prefer-readonly": "error",
      // TanStack Router прерывает beforeLoad/loader только выброшенным `redirect(...)`,
      // а он — `Response`, не `Error`. Разрешён ровно этот тип из пакета роутера.
      "@typescript-eslint/only-throw-error": [
        "error",
        { allow: [{ from: "package", package: "@tanstack/router-core", name: "Redirect" }] },
      ],
    },
  },
  {
    // Дополнение модуля vitest матчерами jest-dom: слияние объявлений требует пустого
    // интерфейса с теми же параметрами типа, что у vitest, включая неиспользуемый `T`.
    files: ["src/vitest-dom.d.ts"],
    rules: {
      "@typescript-eslint/no-empty-object-type": "off",
      "@typescript-eslint/no-unused-vars": "off",
    },
  },
  {
    files: ["**/*.js"],
    extends: [js.configs.recommended],
    languageOptions: { ecmaVersion: 2023, globals: globals.node },
  },
);
