import { useForm } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { GroupDetailResponse } from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { groupNameSchema } from "./groupName";

/**
 * Панель справа для изменения названия группы [group]. Дисциплины и тренеры меняются своими
 * секциями карточки, поэтому здесь отправляются без изменений — такими, как загружены в карточке.
 */
export function GroupRenameSheet({
  api,
  open,
  onOpenChange,
  group,
}: {
  readonly api: ApiClient;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly group: GroupDetailResponse;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("groups.edit")}>
      <GroupRenameForm api={api} group={group} />
    </EditSheet>
  );
}

/** Форма названия группы; монтируется при каждом открытии панели. */
function GroupRenameForm({
  api,
  group,
}: {
  readonly api: ApiClient;
  readonly group: GroupDetailResponse;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const schema = useMemo(() => groupNameSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);

  const form = useForm({
    defaultValues: { name: group.name },
    onSubmit: async ({ value }) => {
      const name = schema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("groups/edit", {
        id: group.id,
        name: name.data,
        disciplineIds: group.disciplines.map((d) => d.id),
        employeeIds: group.employees.map((e) => e.id),
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] }),
        queryClient.invalidateQueries({
          queryKey: ["api", branchId, "groups/detail", { id: group.id }],
        }),
      ]);
      toast.success(t("groups.detail.renamed"));
      close();
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
          <form.Field name="name" validators={{ onSubmit: schema }}>
            {(field) => (
              <TextField field={field} label={t("groups.name")} autoComplete="off" required />
            )}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
