import { useId, useState, type InputHTMLAttributes, type SelectHTMLAttributes } from "react";
import { t } from "../i18n";

const controlClass =
  "block w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base text-slate-900 " +
  "shadow-xs outline-none transition placeholder:text-slate-400 " +
  "focus:border-brand-500 focus:ring-4 focus:ring-brand-100 disabled:opacity-60 " +
  "dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:ring-brand-500/25";

/** Подпись [label] и подсказка [hint] вокруг поля с идентификатором [id]. */
function Field({
  id,
  label,
  hint,
  children,
}: {
  id: string;
  label: string;
  hint?: string | undefined;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700 dark:text-slate-300">
        {label}
      </label>
      {children}
      {hint !== undefined && hint !== "" && (
        <p className="text-xs text-slate-500 dark:text-slate-400">{hint}</p>
      )}
    </div>
  );
}

/** Текстовое поле с подписью [label]. Остальные свойства уходят в нативный `<input>`. */
export function TextField({
  label,
  hint,
  ...input
}: { label: string; hint?: string } & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <Field id={id} label={label} hint={hint}>
      <input id={id} className={controlClass} {...input} />
    </Field>
  );
}

/** Поле пароля с кнопкой показа введённого значения. */
export function PasswordField({
  label,
  ...input
}: { label: string } & Omit<InputHTMLAttributes<HTMLInputElement>, "type">) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  return (
    <Field id={id} label={label}>
      <div className="relative">
        <input
          id={id}
          type={visible ? "text" : "password"}
          className={`${controlClass} pr-11`}
          {...input}
        />
        <button
          type="button"
          onClick={() => {
            setVisible((v) => !v);
          }}
          aria-label={visible ? t.passwordHide : t.passwordShow}
          aria-pressed={visible}
          className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
        >
          <EyeIcon crossed={visible} />
        </button>
      </div>
    </Field>
  );
}

/** Выпадающий список с подписью [label]; варианты — [options]. */
export function SelectField({
  label,
  hint,
  options,
  ...select
}: {
  label: string;
  hint?: string;
  options: readonly { value: string; label: string }[];
} & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <Field id={id} label={label} hint={hint}>
      <select id={id} className={controlClass} {...select}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </Field>
  );
}

/** Иконка «глаз»; [crossed] — перечёркнутый вариант. */
function EyeIcon({ crossed }: { crossed: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="M4 4l16 16" strokeLinecap="round" />}
    </svg>
  );
}
