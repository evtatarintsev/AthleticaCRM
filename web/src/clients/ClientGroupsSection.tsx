import { useQuery, useQueryClient } from "@tanstack/react-query";
import { XIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientGroup, ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { ChecklistSheet } from "@/ui/ChecklistSheet";

/**
 * Группы клиента: чипы с удалением и запись сразу в несколько групп через панель с чекбоксами
 * одной операцией — во все отмеченные или ни в одну; группы, в которых клиент уже состоит, видны
 * в панели заблокированными.
 */
export function ClientGroupsSection({
  api,
  branchId,
  clientId,
  groups,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly groups: readonly ClientGroup[];
  readonly onChanged: () => Promise<void>;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [sheetOpen, setSheetOpen] = useState(false);
  const allGroups = useQuery({
    ...apiQuery(api, branchId, "groups/list-for-select"),
    enabled: sheetOpen,
  });

  const refresh = async () => {
    await Promise.all([
      onChanged(),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] }),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] }),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/detail"] }),
    ]);
  };

  const removeGroup = async (groupId: ClientGroup["id"]) => {
    const result = await api.call("clients/remove-from-group", { clientIds: [clientId], groupId });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await refresh();
  };

  const addToGroups = async (groupIds: readonly ClientGroup["id"][]) => {
    const result = await api.call("clients/add-to-groups", {
      clientId,
      groupIds: [...groupIds],
    });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    toast.success(t("clients.detail.groupsAdded", { count: groupIds.length }));
    return null;
  };

  const memberIds = new Set(groups.map((group) => group.id));

  return (
    <div className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-base font-semibold">{t("clients.column.groups")}</h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => {
            setSheetOpen(true);
          }}
        >
          {t("clients.detail.addToGroup")}
        </Button>
      </div>

      {groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("clients.detail.noGroups")}</p>
      ) : (
        <div className="flex flex-wrap gap-1">
          {groups.map((group) => (
            <span
              key={group.id}
              className="flex items-center gap-1 rounded-full border bg-secondary px-2 py-0.5 text-xs text-secondary-foreground"
            >
              {group.name}
              <button
                type="button"
                aria-label={t("clients.detail.removeFromGroupAria", { name: group.name })}
                onClick={() => {
                  void removeGroup(group.id);
                }}
                className="rounded-full hover:text-destructive"
              >
                <XIcon aria-hidden className="size-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      <ChecklistSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        title={t("clients.detail.addToGroupsTitle")}
        items={(allGroups.data ?? []).map((group) => ({
          id: group.id,
          name: group.name,
          disabledReason: memberIds.has(group.id) ? t("groups.detail.alreadyInGroup") : undefined,
        }))}
        selected={[]}
        emptyText={t("groups.empty")}
        searchLabel={t("clients.detail.searchGroup")}
        submitLabel={t("action.add")}
        requireSelection
        onSubmit={addToGroups}
      />
    </div>
  );
}
