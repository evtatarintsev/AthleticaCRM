import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { ClientCardSectionsResponse } from "@/api/generated/contracts";
import { CheckboxField } from "@/forms/fields";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { clientCardSectionsQuery } from "./card/clientCardQueries";

export function ClientCardSectionsSheet({
  api,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("clientOverview.sections")}>
      <Panel api={api} />
    </EditSheet>
  );
}
function Panel({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const queryClient = useQueryClient();
  const query = clientCardSectionsQuery(api, branchId);
  const settings = useQuery(query);
  if (settings.data === undefined)
    return (
      <EditSheetBody>
        {settings.isError ? (
          <FormAlert message={t("clients.loadClientError")} />
        ) : (
          t("clientOverview.loading")
        )}
      </EditSheetBody>
    );
  return (
    <SectionsForm
      values={settings.data}
      onSave={async (values) => {
        const result = await api.call("org/client-card/update", values);
        if (!result.ok) return apiErrorMessage(t, result.error);
        queryClient.setQueryData(query.queryKey, result.value);
        toast.success(t("clientOverview.saved"));
        return null;
      }}
    />
  );
}
function SectionsForm({
  values,
  onSave,
}: {
  readonly values: ClientCardSectionsResponse;
  readonly onSave: (values: ClientCardSectionsResponse) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [error, setError] = useState<string | null>(null);
  const form = useForm({
    defaultValues: { ...values },
    onSubmit: async ({ value }) => {
      const failure = await onSave(value);
      setError(failure);
      if (failure === null) close();
    },
  });
  return (
    <form.Subscribe selector={(s) => ({ dirty: s.isDirty, submitting: s.isSubmitting })}>
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          <p className="text-sm text-muted-foreground">{t("clientOverview.sectionsHint")}</p>
          {error !== null && <FormAlert message={error} />}
          {(["tasks", "notes", "chat", "documents"] as const).map((name) => (
            <form.Field key={name} name={name}>
              {(field) => (
                <CheckboxField
                  field={field}
                  label={t(`clientOverview.section.${name}`)}
                  hint={t(`clientOverview.hint.${name}`)}
                />
              )}
            </form.Field>
          ))}
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
