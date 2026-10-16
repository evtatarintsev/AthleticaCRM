import { useForm } from "@tanstack/react-form";
import { ArchiveIcon, ArchiveRestoreIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { z } from "zod";
import {
  DurationUnitSchema,
  type Currency,
  type DurationUnit,
  type Money,
  type TariffPlanSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { CURRENCIES } from "@/lib/currency";
import { moneyEditText, parseMoney } from "@/lib/money";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Условия тарифа из формы: всё, кроме идентификатора и признака архива. */
export interface TariffTerms {
  readonly name: string;
  /** Число занятий; `null` — безлимит. */
  readonly sessions: number | null;
  readonly durationValue: number;
  readonly durationUnit: DurationUnit;
  readonly price: Money;
}

/** Значения формы тарифа: числа и сумма вводятся текстом. */
interface TariffFormValues {
  readonly name: string;
  readonly unlimited: boolean;
  readonly sessions: string;
  readonly durationValue: string;
  readonly durationUnit: DurationUnit;
  readonly price: string;
}

/** Наибольшее значение `Int` в Kotlin: больше сервер не примет. */
const MAX_INT32 = 2_147_483_647;

/** Положительное целое из текста [text] в пределах `Int`, иначе `null`. */
function positiveInt(text: string): number | null {
  const value = /^\d+$/.test(text.trim()) ? Number(text.trim()) : Number.NaN;
  return Number.isInteger(value) && value > 0 && value <= MAX_INT32 ? value : null;
}

/** Схема формы тарифа с сообщениями на языке [t]; сумма разбирается в валюте [currency]. */
function tariffSchema(t: I18n["t"], currency: Currency) {
  return z
    .object({
      name: z.string().trim().min(1, t("error.required")),
      unlimited: z.boolean(),
      sessions: z.string(),
      durationValue: z.string().refine((v) => positiveInt(v) !== null, t("error.positiveInteger")),
      durationUnit: DurationUnitSchema,
      price: z.string().refine((v) => parseMoney(v, currency) !== null, t("error.invalidAmount")),
    })
    .refine((v) => v.unlimited || positiveInt(v.sessions) !== null, {
      path: ["sessions"],
      message: t("error.positiveInteger"),
    });
}

/** Свойства панели тарифа. */
interface TariffSheetProps {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /**
   * Редактируемый тариф; `null` — новый. Владелец передаёт актуальное состояние тарифа:
   * по нему панель показывает признак архива, а поля формы остаются как при открытии.
   */
  readonly initial: TariffPlanSchema | null;
  /** Валюта организации: в ней вводится стоимость. */
  readonly currency: Currency;
  /** Сохраняет условия [TariffTerms]; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (terms: TariffTerms) => Promise<string | null>;
  /**
   * Переносит редактируемый тариф в архив или восстанавливает его; возвращает текст ошибки
   * или `null` при успехе. Без обработчика действий архива в панели нет.
   */
  readonly onArchiveToggle?: (() => Promise<string | null>) | undefined;
}

/**
 * Панель создания или изменения тарифа: название, число занятий или безлимит, срок и стоимость;
 * при изменении — перенос в архив и восстановление. Что делать с сохранённым тарифом, решает
 * владелец панели через [TariffSheetProps.onSave].
 */
export function TariffSheet({ open, onOpenChange, initial, ...rest }: TariffSheetProps) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={initial === null ? t("tariffs.create") : t("tariffs.edit")}
    >
      <TariffForm initial={initial} {...rest} />
    </EditSheet>
  );
}

/** Форма тарифа; после сохранения закрывает свою панель. */
function TariffForm({
  initial,
  currency,
  onSave,
  onArchiveToggle,
}: Omit<TariffSheetProps, "open" | "onOpenChange">) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const schema = useMemo(() => tariffSchema(t, currency), [t, currency]);
  const [failure, setFailure] = useState<string | null>(null);
  const symbol = CURRENCIES.find((c) => c.code === currency)?.symbol ?? currency;
  const defaultValues: TariffFormValues = {
    name: initial?.name ?? "",
    unlimited: initial !== null && initial.sessions === null,
    sessions: initial?.sessions?.toString() ?? "",
    durationValue: initial?.durationValue.toString() ?? "1",
    durationUnit: initial?.durationUnit ?? "MONTHS",
    price: initial === null ? "" : moneyEditText(initial.price),
  };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const durationValue = positiveInt(value.durationValue);
      const price = parseMoney(value.price, currency);
      const sessions = value.unlimited ? null : positiveInt(value.sessions);
      if (durationValue === null || price === null || (!value.unlimited && sessions === null)) {
        return;
      }
      setFailure(null);
      const error = await onSave({
        name: value.name.trim(),
        sessions,
        durationValue,
        durationUnit: value.durationUnit,
        price,
      });
      if (error === null) {
        close();
      } else {
        setFailure(error);
      }
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="name">
            {(field) => (
              <TextField field={field} label={t("tariffs.name")} autoComplete="off" required />
            )}
          </form.Field>
          <form.Field name="unlimited">
            {(field) => <CheckboxField field={field} label={t("tariffs.unlimited")} />}
          </form.Field>
          <form.Subscribe selector={(state) => state.values.unlimited}>
            {(unlimited) =>
              !unlimited && (
                <form.Field name="sessions">
                  {(field) => (
                    <TextField
                      field={field}
                      label={t("tariffs.sessions")}
                      inputMode="numeric"
                      autoComplete="off"
                      required
                    />
                  )}
                </form.Field>
              )
            }
          </form.Subscribe>
          <div className="grid gap-4 sm:grid-cols-2">
            <form.Field name="durationValue">
              {(field) => (
                <TextField
                  field={field}
                  label={t("tariffs.duration")}
                  inputMode="numeric"
                  autoComplete="off"
                  required
                />
              )}
            </form.Field>
            <form.Field name="durationUnit">
              {(field) => (
                <RadioGroupField
                  field={field}
                  label={t("tariffs.durationUnit")}
                  options={[
                    { value: "DAYS", label: t("tariffs.days") },
                    { value: "MONTHS", label: t("tariffs.months") },
                  ]}
                />
              )}
            </form.Field>
          </div>
          <form.Field name="price">
            {(field) => (
              <TextField
                field={field}
                label={t("tariffs.price", { currency: symbol })}
                inputMode="decimal"
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          {initial !== null && onArchiveToggle !== undefined && (
            <ArchiveSection archived={initial.archived} onToggle={onArchiveToggle} />
          )}
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}

/**
 * Блок архива тарифа: перенос в архив или восстановление по признаку [archived]. Действие
 * [onToggle] выполняется сразу и не затрагивает черновик формы.
 */
function ArchiveSection({
  archived,
  onToggle,
}: {
  archived: boolean;
  onToggle: () => Promise<string | null>;
}) {
  const { t } = useI18n();
  const [pending, setPending] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const toggle = async () => {
    setPending(true);
    setFailure(null);
    const error = await onToggle();
    setPending(false);
    setFailure(error);
  };

  return (
    <section className="space-y-3 border-t pt-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-medium">{t("tariffs.archiveSection")}</h3>
        {archived && (
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {t("tariffs.archived")}
          </span>
        )}
      </div>
      <p className="text-sm text-muted-foreground">
        {archived ? t("tariffs.archivedHint") : t("tariffs.archiveHint")}
      </p>
      {failure !== null && <FormAlert message={failure} />}
      <Button
        type="button"
        variant="outline"
        disabled={pending}
        aria-busy={pending}
        onClick={() => {
          void toggle();
        }}
      >
        {archived ? <ArchiveRestoreIcon aria-hidden /> : <ArchiveIcon aria-hidden />}
        {archived ? t("tariffs.restore") : t("tariffs.archive")}
      </Button>
    </section>
  );
}
