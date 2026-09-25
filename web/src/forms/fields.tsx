import { EyeIcon, EyeOffIcon } from "lucide-react";
import { useId, useState, type InputHTMLAttributes, type ReactNode } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { useI18n } from "@/i18n/context";
import { fieldError, type BoundField } from "./field";

/** Атрибуты `<input>`, которыми управляет обёртка поля. */
type ManagedInputProps = "id" | "name" | "value" | "defaultValue" | "onChange" | "onBlur" | "type";

/** Общие свойства обёрток: поле формы [field], подпись [label] и подсказка [hint]. */
interface FieldProps<T> {
  readonly field: BoundField<T>;
  readonly label: string;
  readonly hint?: string;
}

/**
 * Подпись, подсказка и ошибка вокруг элемента управления. [control] получает атрибуты,
 * связывающие его с подписью и ошибкой: `id`, `aria-invalid`, `aria-describedby`.
 */
function FieldFrame({
  label,
  hint,
  error,
  control,
}: {
  label: string;
  hint: string | undefined;
  error: string | null;
  control: (aria: {
    id: string;
    "aria-invalid": boolean;
    "aria-describedby": string | undefined;
  }) => ReactNode;
}) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [error === null ? null : errorId, hint === undefined ? null : hintId]
    .filter((part) => part !== null)
    .join(" ");
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {control({
        id,
        "aria-invalid": error !== null,
        "aria-describedby": describedBy === "" ? undefined : describedBy,
      })}
      {error !== null && (
        <p id={errorId} className="text-sm text-destructive">
          {error}
        </p>
      )}
      {hint !== undefined && (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
    </div>
  );
}

/** Текстовое поле. Остальные свойства (`autoComplete`, `inputMode`, …) уходят в `<input>`. */
export function TextField({
  field,
  label,
  hint,
  type = "text",
  ...input
}: FieldProps<string> & {
  type?: "text" | "email" | "tel";
} & Omit<InputHTMLAttributes<HTMLInputElement>, ManagedInputProps>) {
  return (
    <FieldFrame
      label={label}
      hint={hint}
      error={fieldError(field)}
      control={(aria) => (
        <Input
          {...input}
          {...aria}
          type={type}
          name={field.name}
          value={field.state.value}
          onChange={(event) => {
            field.handleChange(event.target.value);
          }}
          onBlur={() => {
            field.handleBlur();
          }}
        />
      )}
    />
  );
}

/** Поле пароля с кнопкой показа введённого значения. */
export function PasswordField({
  field,
  label,
  hint,
  ...input
}: FieldProps<string> & Omit<InputHTMLAttributes<HTMLInputElement>, ManagedInputProps>) {
  const { t } = useI18n();
  const [visible, setVisible] = useState(false);
  return (
    <FieldFrame
      label={label}
      hint={hint}
      error={fieldError(field)}
      control={(aria) => (
        <div className="relative">
          <Input
            {...input}
            {...aria}
            type={visible ? "text" : "password"}
            name={field.name}
            value={field.state.value}
            onChange={(event) => {
              field.handleChange(event.target.value);
            }}
            onBlur={() => {
              field.handleBlur();
            }}
            className="pr-10"
          />
          <button
            type="button"
            onClick={() => {
              setVisible((v) => !v);
            }}
            aria-label={visible ? t("auth.passwordHide") : t("auth.passwordShow")}
            aria-pressed={visible}
            className="absolute inset-y-0 right-0 grid w-10 place-items-center rounded-r-md text-muted-foreground outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50"
          >
            {visible ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
          </button>
        </div>
      )}
    />
  );
}

/** Вариант выпадающего списка со значением [value] и подписью [label]. */
export interface SelectOption<T extends string> {
  readonly value: T;
  readonly label: string;
}

/** Выпадающий список на нативном `<select>`; значение поля — одно из [options]. */
export function SelectField<T extends string>({
  field,
  label,
  hint,
  options,
  autoComplete,
}: FieldProps<T> & {
  options: readonly SelectOption<T>[];
  autoComplete?: string;
}) {
  return (
    <FieldFrame
      label={label}
      hint={hint}
      error={fieldError(field)}
      control={(aria) => (
        <NativeSelect
          {...aria}
          name={field.name}
          value={field.state.value}
          autoComplete={autoComplete}
          onChange={(event) => {
            const option = options.find((o) => o.value === event.target.value);
            if (option !== undefined) {
              field.handleChange(option.value);
            }
          }}
          onBlur={() => {
            field.handleBlur();
          }}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </NativeSelect>
      )}
    />
  );
}

/** Флажок с подписью [label]; значение поля — `boolean`. */
export function CheckboxField({ field, label, hint }: FieldProps<boolean>) {
  const id = useId();
  const hintId = `${id}-hint`;
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="flex items-center gap-2 text-sm font-medium">
        <input
          id={id}
          type="checkbox"
          name={field.name}
          checked={field.state.value}
          aria-describedby={hint === undefined ? undefined : hintId}
          onChange={(event) => {
            field.handleChange(event.target.checked);
          }}
          onBlur={() => {
            field.handleBlur();
          }}
          className="size-4 accent-primary"
        />
        {label}
      </label>
      {hint !== undefined && (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      )}
    </div>
  );
}

/** Группа переключателей: значение поля — одно из [options]. */
export function RadioGroupField<T extends string>({
  field,
  label,
  options,
}: FieldProps<T> & { options: readonly SelectOption<T>[] }) {
  return (
    <fieldset className="space-y-1.5">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <label
            key={option.value}
            className="flex cursor-pointer items-center gap-2 rounded-md border px-3 py-1.5 text-sm has-checked:border-primary has-checked:bg-primary/10 has-focus-visible:ring-[3px] has-focus-visible:ring-ring/50"
          >
            <input
              type="radio"
              name={field.name}
              value={option.value}
              checked={field.state.value === option.value}
              onChange={() => {
                field.handleChange(option.value);
              }}
              onBlur={() => {
                field.handleBlur();
              }}
              className="size-4 accent-primary"
            />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/** Многострочное текстовое поле. */
export function TextAreaField({
  field,
  label,
  hint,
  rows = 4,
}: FieldProps<string> & { rows?: number }) {
  return (
    <FieldFrame
      label={label}
      hint={hint}
      error={fieldError(field)}
      control={(aria) => (
        <textarea
          {...aria}
          name={field.name}
          rows={rows}
          value={field.state.value}
          onChange={(event) => {
            field.handleChange(event.target.value);
          }}
          onBlur={() => {
            field.handleBlur();
          }}
          className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 aria-invalid:border-destructive md:text-sm dark:bg-input/30"
        />
      )}
    />
  );
}
