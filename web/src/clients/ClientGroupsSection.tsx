import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientGroup, ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { ChecklistSheet } from "@/ui/ChecklistSheet";
import { todayLocalDate } from "@/lib/localDate";
import { BlockStatus, ClientCardBlock } from "./card/ClientCardBlock";

/**
 * Блок групп клиента: список групп со ссылками и удалением, запись сразу в несколько групп
 * через панель с чекбоксами одной операцией — во все отмеченные или ни в одну; группы,
 * в которых клиент уже состоит, видны в панели заблокированными.
 */
export function ClientGroupsSection({
  api,
  branchId,
  clientId,
  groups,
  onChanged,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly groups: readonly ClientGroup[];
  readonly onChanged: () => Promise<void>;
  readonly open?: boolean;
  readonly onOpenChange?: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [localOpen, setLocalOpen] = useState(false);
  const sheetOpen = open ?? localOpen;
  const setSheetOpen = onOpenChange ?? setLocalOpen;
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
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "schedule/list"] }),
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

  const details = useQueries({
    queries: groups.map((group) => apiQuery(api, branchId, "groups/detail", { id: group.id })),
  });
  const memberIds = new Set(groups.map((group) => group.id));

  return (
    <>
      <ClientCardBlock
        title={t("clientCard.block.groups")}
        count={groups.length}
        actions={
          <Button
            variant="link"
            size="sm"
            onClick={() => {
              setSheetOpen(true);
            }}
          >
            <PlusIcon aria-hidden />
            {t("clients.detail.addToGroup")}
          </Button>
        }
      >
        <BlockStatus
          isPending={false}
          isError={false}
          isEmpty={groups.length === 0}
          empty={t("clients.detail.noGroups")}
        />
        {groups.length > 0 && (
          <ul className="flex flex-wrap gap-2 px-4 py-3.5">
            {groups.map((group, index) => {
              const detail = details[index]?.data;
              const days = [
                ...new Set(
                  detail?.schedule
                    .filter(
                      (slot) =>
                        slot.validity === null ||
                        (slot.validity.from <= todayLocalDate() &&
                          (slot.validity.to === null || slot.validity.to > todayLocalDate())),
                    )
                    .map((slot) => t(`day.${slot.dayOfWeek}`)),
                ),
              ].join(" ");
              const sub = [detail?.disciplines.map((d) => d.name).join(", ") ?? "", days]
                .filter((part) => part !== "")
                .join(" · ");
              return (
                <li
                  key={group.id}
                  className="inline-flex min-h-[34px] max-w-full items-center gap-2 rounded-[10px] border bg-muted/50 py-1 pr-1.5 pl-3 text-sm"
                >
                  <Link
                    to="/groups/$groupId"
                    params={{ groupId: group.id }}
                    className="truncate font-medium text-primary hover:underline"
                  >
                    {group.name}
                  </Link>
                  {sub !== "" && (
                    <span className="truncate text-xs text-muted-foreground">{sub}</span>
                  )}
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    className="shrink-0 text-muted-foreground"
                    aria-label={t("clients.detail.removeFromGroupAria", { name: group.name })}
                    title={t("clients.detail.removeFromGroupAria", { name: group.name })}
                    onClick={() => {
                      void removeGroup(group.id);
                    }}
                  >
                    <XIcon aria-hidden />
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </ClientCardBlock>

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
    </>
  );
}
