import { useForm } from "@tanstack/react-form";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import {
  LocalDateSchema,
  type ClientContactInput,
  type ClientDetailResponse,
  type CustomFieldValue,
  type Gender,
  type LeadSourceId,
  type LocalDate,
  type UploadId,
} from "@/api/generated/contracts";
import { AvatarPicker } from "@/account/AvatarPicker";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { RadioGroupField, SelectField, TextField, type SelectOption } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { ClientContactsFields } from "./ClientContactsFields";
import { contactEntriesOf, contactInputsOf, type ContactFormEntry } from "./clientContacts";
import { ClientCustomFieldsFields } from "./ClientCustomFieldsFields";
import { clientCustomFieldsQuery } from "./clientsQueries";

/** Данные формы клиента, готовые к отправке на сервер: без идентификатора. */
export interface ClientSaveInput {
  readonly name: string;
  readonly avatarId: UploadId | null;
  readonly birthday: LocalDate | null;
  readonly gender: Gender;
  readonly leadSourceId: LeadSourceId | null;
  readonly customFields: readonly CustomFieldValue[];
  readonly contacts: readonly ClientContactInput[];
}

/** Значение поля источника клиента: идентификатор или пустая строка — источник не выбран. */
type LeadSourceFieldValue = LeadSourceId | "";

/** Значения формы TanStack Form: контакты и кастомные поля живут в собственном состоянии. */
interface ClientFormValues {
  readonly name: string;
  readonly gender: Gender;
  readonly birthday: string;
  readonly avatarId: UploadId | null;
  readonly leadSourceId: LeadSourceFieldValue;
}

/** Схема имени клиента с сообщением на языке [t]; обрезается по краям. */
function nameSchema(t: I18n["t"]) {
  return z.string().trim().min(1, t("error.required"));
}

/**
 * Форма создания или редактирования клиента: основные поля, контакты и дополнительные поля.
 * [initial] — редактируемый клиент; `null` — новый. Сохраняет через [onSave], которая
 * возвращает текст ошибки сервера или `null` при успехе.
 */
export function ClientFormPage({
  api,
  title,
  submitLabel,
  initial,
  onSave,
}: {
  readonly api: ApiClient;
  readonly title: string;
  /** Подпись кнопки отправки: «Создать» для нового клиента, «Сохранить» для изменений. */
  readonly submitLabel: string;
  readonly initial: ClientDetailResponse | null;
  readonly onSave: (input: ClientSaveInput) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const leadSources = useQuery(apiQuery(api, branchId, "lead-sources/list"));
  const customFieldDefs = useQuery(clientCustomFieldsQuery(api, branchId));
  const schema = useMemo(() => nameSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const [contacts, setContacts] = useState<readonly ContactFormEntry[]>(() =>
    contactEntriesOf(initial?.contacts ?? []),
  );
  const [customFields, setCustomFields] = useState<readonly CustomFieldValue[]>(
    () => initial?.customFields ?? [],
  );

  const defaultValues: ClientFormValues = {
    name: initial?.name ?? "",
    gender: initial?.gender ?? "MALE",
    birthday: initial?.birthday ?? "",
    avatarId: initial?.avatarId ?? null,
    leadSourceId: initial?.leadSourceId ?? "",
  };

  const form = useForm({
    defaultValues,
    onSubmit: async ({ value }) => {
      const name = schema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const error = await onSave({
        name: name.data,
        avatarId: value.avatarId,
        birthday: value.birthday === "" ? null : LocalDateSchema.parse(value.birthday),
        gender: value.gender,
        leadSourceId: value.leadSourceId === "" ? null : value.leadSourceId,
        customFields,
        contacts: contactInputsOf(contacts),
      });
      if (error !== null) {
        setFailure(error);
      }
    },
  });

  const leadSourceOptions: readonly SelectOption<LeadSourceFieldValue>[] = [
    { value: "", label: t("clients.leadSourceNone") },
    ...(leadSources.data?.leadSources ?? []).map((source) => ({
      value: source.id,
      label: source.name,
    })),
  ];

  return (
    <section className="max-w-2xl">
      <PageHeader title={title} />
      <form
        method="post"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void form.handleSubmit();
        }}
        className="space-y-6"
      >
        {failure !== null && <FormAlert message={failure} />}

        <form.Subscribe selector={(state) => [state.isSubmitting, state.values.name] as const}>
          {([submitting, name]) => (
            <form.Field name="avatarId">
              {(field) => (
                <AvatarPicker
                  api={api}
                  value={field.state.value}
                  name={name}
                  disabled={submitting}
                  onChange={(id) => {
                    field.handleChange(id);
                  }}
                />
              )}
            </form.Field>
          )}
        </form.Subscribe>

        <form.Field name="name" validators={{ onSubmit: schema }}>
          {(field) => (
            <TextField field={field} label={t("clients.name")} autoComplete="name" required />
          )}
        </form.Field>

        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <ClientContactsFields entries={contacts} onChange={setContacts} disabled={submitting} />
          )}
        </form.Subscribe>

        <form.Field name="gender">
          {(field) => (
            <RadioGroupField
              field={field}
              label={t("clients.gender")}
              options={[
                { value: "MALE", label: t("gender.MALE") },
                { value: "FEMALE", label: t("gender.FEMALE") },
              ]}
            />
          )}
        </form.Field>

        <form.Field name="leadSourceId">
          {(field) => (
            <SelectField
              field={field}
              label={t("clients.leadSource")}
              options={leadSourceOptions}
            />
          )}
        </form.Field>

        <form.Field name="birthday">
          {(field) => <TextField field={field} label={t("clients.birthday")} type="date" />}
        </form.Field>

        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <ClientCustomFieldsFields
              definitions={customFieldDefs.data ?? []}
              values={customFields}
              onChange={setCustomFields}
              disabled={submitting}
            />
          )}
        </form.Subscribe>

        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting} aria-busy={submitting}>
              {submitLabel}
            </Button>
          )}
        </form.Subscribe>
      </form>
    </section>
  );
}
