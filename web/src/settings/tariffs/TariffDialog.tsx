import { useForm } from "@tanstack/react-form";
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
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, RadioGroupField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { CURRENCIES } from "@/lib/currency";
import { moneyEditText, parseMoney } from "@/lib/money";

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

/** Свойства диалога тарифа. */
interface TariffDialogProps {
  /** Заголовок: создание или редактирование. */
  readonly title: string;
  /** Редактируемый тариф; `null` — новый. */
  readonly initial: TariffPlanSchema | null;
  /** Валюта организации: в ней вводится стоимость. */
  readonly currency: Currency;
  /** Сохраняет условия [terms]; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (terms: TariffTerms) => Promise<string | null>;
  /** Закрывает диалог. */
  readonly onClose: () => void;
}

/** Диалог создания или изменения тарифа: название, число занятий или безлимит, срок, стоимость. */
export function TariffDialog({ title, initial, currency, onSave, onClose }: TariffDialogProps) {
  const { t } = useI18n();
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
        onClose();
      } else {
        setFailure(error);
      }
    },
  });

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent aria-describedby={undefined} closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <form
          method="post"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
          className="space-y-4"
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
          <DialogFooter closeLabel={t("action.cancel")}>
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <Button type="submit" disabled={submitting} aria-busy={submitting}>
                  {t("action.save")}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
