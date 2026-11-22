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
import { clientUpcomingQuery, upcomingSessions } from "./card/clientCardQueries";
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
  const { t, format } = useI18n();
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
  const upcoming = useQuery(clientUpcomingQuery(api, branchId, groups));
  const nearest = upcomingSessions(upcoming.data?.sessions);
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
          <ul className="divide-y">
            {groups.map((group, index) => {
              const detail = details[index]?.data;
              const slots =
                detail?.schedule.filter(
                  (slot) =>
                    slot.validity === null ||
                    (slot.validity.from <= todayLocalDate() &&
                      (slot.validity.to === null || slot.validity.to > todayLocalDate())),
                ) ?? [];
              const next = nearest.find((session) => session.group.id === group.id);
              return (
                <li
                  key={group.id}
                  className="flex items-start justify-between gap-3 px-4 py-3 text-sm"
                >
                  <div className="min-w-0 space-y-1">
                    <Link
                      to="/groups/$groupId"
                      params={{ groupId: group.id }}
                      className="block truncate font-medium text-primary hover:underline"
                    >
                      {group.name}
                    </Link>
                    {detail !== undefined && (
                      <>
                        <p className="text-xs break-words text-muted-foreground">
                          {detail.disciplines.map((d) => d.name).join(", ")}
                        </p>
                        {slots.map((slot, slotIndex) => (
                          <p key={slotIndex} className="text-xs break-words text-muted-foreground">
                            {t(`day.${slot.dayOfWeek}`)} · {format.time(slot.startAt)}–
                            {format.time(slot.endAt)} · {slot.hallName}
                          </p>
                        ))}
                      </>
                    )}
                    {upcoming.isPending && groups.length > 0 ? (
                      <p>{t("clientOverview.loading")}</p>
                    ) : upcoming.isError ? (
                      <p className="text-destructive">{t("clients.loadClientError")}</p>
                    ) : next === undefined ? (
                      <p className="text-xs break-words text-muted-foreground">
                        {t("clientOverview.noUpcoming")}
                      </p>
                    ) : (
                      <Link
                        to="/sessions/$sessionId"
                        params={{ sessionId: next.id }}
                        className="block rounded text-xs text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring"
                        aria-label={t("clientOverview.openSession", { name: group.name })}
                      >
                        {t("clientOverview.nearest")} · {format.date(next.date)},{" "}
                        {format.time(next.startTime)} →
                      </Link>
                    )}
                  </div>
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
