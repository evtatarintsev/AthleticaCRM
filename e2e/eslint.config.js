import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";

/**
 * Строгий анализ e2e-сценариев: те же правила для TypeScript, что в `web/eslint.config.js`,
 * без плагинов React. Директивы `eslint-disable` в исходниках не действуют.
 */
export default tseslint.config(
  { ignores: ["node_modules", "playwright-report", "test-results"] },
  {
    linterOptions: {
      noInlineConfig: true,
      reportUnusedDisableDirectives: "error",
    },
  },
  {
    files: ["**/*.ts"],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.strictTypeChecked,
      ...tseslint.configs.stylisticTypeChecked,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.node,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
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
    },
  },
  {
    files: ["**/*.js"],
    extends: [js.configs.recommended],
    languageOptions: { ecmaVersion: 2023, globals: globals.node },
  },
);
