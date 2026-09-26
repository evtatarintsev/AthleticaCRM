import { useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import { GroupIdSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { GroupFormPage, type GroupSaveInput } from "./GroupFormPage";

/** Создание новой группы: сохраняет и переходит на её карточку. */
export function GroupCreatePage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;

  const save = async (input: GroupSaveInput): Promise<string | null> => {
    const id = GroupIdSchema.parse(uuidv7());
    const result = await api.call("groups/create", { id, ...input });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] });
    await navigate({ to: "/groups/$groupId", params: { groupId: id } });
    return null;
  };

  return (
    <GroupFormPage
      api={api}
      title={t("groups.create")}
      submitLabel={t("action.create")}
      initial={null}
      onSave={save}
    />
  );
}
