import { useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import type { GroupId } from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { GroupFormPage, type GroupSaveInput } from "./GroupFormPage";
import { useGroup } from "./groupsQueries";

/** Редактирование группы [groupId]: сохраняет и возвращается на карточку. */
export function GroupEditPage({
  api,
  groupId,
}: {
  readonly api: ApiClient;
  readonly groupId: GroupId;
}) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const group = useGroup(api, branchId, groupId);

  const save = async (input: GroupSaveInput): Promise<string | null> => {
    const result = await api.call("groups/edit", { id: groupId, ...input });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] }),
      queryClient.invalidateQueries({
        queryKey: ["api", branchId, "groups/detail", { id: groupId }],
      }),
    ]);
    await navigate({ to: "/groups/$groupId", params: { groupId } });
    return null;
  };

  if (group.isPending) {
    return (
      <section className="max-w-2xl">
        <PageHeader title={t("groups.edit")} />
      </section>
    );
  }
  if (group.data === undefined) {
    return (
      <section className="max-w-2xl">
        <PageHeader title={t("groups.edit")} />
        <FormAlert message={t("groups.detail.loadError")} />
      </section>
    );
  }

  return (
    <GroupFormPage
      api={api}
      title={t("groups.edit")}
      submitLabel={t("action.save")}
      initial={group.data}
      onSave={save}
    />
  );
}
