import { useQueryClient } from "@tanstack/react-query";
import { MoreHorizontalIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type {
  ClientId,
  DisciplineId,
  EmployeeId,
  GroupId,
  LocalDate,
  UploadId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ClientPickerSheet, type PickedClient } from "@/clients/ClientPickerSheet";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { todayLocalDate } from "@/lib/localDate";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { ChecklistSheet } from "@/ui/ChecklistSheet";
import { employeeChecklistItem } from "./employeeChecklistItem";
import { GroupClientsTab } from "./GroupClientsTab";
import { GroupJournalTab } from "./GroupJournalTab";
import { GroupMembershipsTab } from "./GroupMembershipsTab";
import { GroupOverviewTab } from "./GroupOverviewTab";
import { GroupRenameSheet } from "./GroupRenameSheet";
import { GroupScheduleSheet } from "./GroupScheduleSheet";
import { GroupSessionsSection } from "./GroupSessionsSection";
import { cardsToSlotInputs, slotsToCards, type SlotCard } from "./groupSchedule";
import { GROUP_TABS, attentionCounts, type GroupTab, type RosterFilter } from "./groupOverview";
import {
  useGroup,
  useGroupDisciplines,
  useGroupEmployees,
  useGroupHalls,
  useGroupRoster,
} from "./groupsQueries";

/**
 * Карточка группы: шапка с названием, дисциплинами, «Добавить клиентов» и меню «⋯», и вкладки
 * «Обзор», «Клиенты», «Абонементы», «Занятия», «Журнал». Активная вкладка [tab] хранится в адресе,
 * её смена сообщается через [onTabChange]. Дисциплины, тренеры, расписание и название меняются
 * в существующих панелях; изменения сразу отражаются в карточке.
 */
export function GroupDetailPage({
  api,
  groupId,
  tab,
  onTabChange,
}: {
  readonly api: ApiClient;
  readonly groupId: GroupId;
  readonly tab: GroupTab;
  readonly onTabChange: (tab: GroupTab) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const group = useGroup(api, branchId, groupId);
  const roster = useGroupRoster(api, branchId, groupId);
  const disciplines = useGroupDisciplines(api, branchId);
  const employees = useGroupEmployees(api, branchId);
  const halls = useGroupHalls(api, branchId);

  const [picker, setPicker] = useState<"disciplines" | "employees" | null>(null);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [renameOpen, setRenameOpen] = useState(false);
  const [clientPickerOpen, setClientPickerOpen] = useState(false);
  const [clientsFilter, setClientsFilter] = useState<RosterFilter>("all");

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] }),
      queryClient.invalidateQueries({
        queryKey: ["api", branchId, "groups/detail", { id: groupId }],
      }),
    ]);

  const refreshRoster = () =>
    queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/roster", { groupId }] });

  const saveDisciplines = async (
    disciplineIds: readonly DisciplineId[],
  ): Promise<string | null> => {
    const result = await api.call("groups/set-disciplines", { groupId, disciplineIds });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    toast.success(t("groups.detail.disciplinesSaved"));
    return null;
  };

  const saveEmployees = async (employeeIds: readonly EmployeeId[]): Promise<string | null> => {
    const result = await api.call("groups/set-employees", { groupId, employeeIds });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    toast.success(t("groups.detail.employeesSaved"));
    return null;
  };

  const withAvatar = (employee: {
    readonly id: EmployeeId;
    readonly name: string;
    readonly avatarId: UploadId | null;
  }) => employeeChecklistItem(api, employee);

  const refreshClients = () =>
    Promise.all([
      refresh(),
      refreshRoster(),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] }),
    ]);

  const addClients = async (clients: readonly PickedClient[]): Promise<string | null> => {
    const result = await api.call("clients/add-to-group", {
      groupId,
      clientIds: clients.map((client) => client.id),
    });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refreshClients();
    toast.success(t("groups.detail.clientsAdded", { count: clients.length }));
    return null;
  };

  const removeClient = async (clientId: ClientId) => {
    const result = await api.call("clients/remove-from-group", { groupId, clientIds: [clientId] });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await refreshClients();
  };

  const saveSchedule = async (
    cards: readonly SlotCard[],
    effectiveFrom: LocalDate,
  ): Promise<string | null> => {
    const result = await api.call("groups/set-schedule", {
      groupId,
      effectiveFrom: effectiveFrom === todayLocalDate() ? null : effectiveFrom,
      slots: cardsToSlotInputs(cards),
    });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    toast.success(t("groups.schedule.savedToast"));
    return null;
  };

  if (group.isPending) {
    return (
      <section className="max-w-3xl space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-24 w-full" />
      </section>
    );
  }
  if (group.data === undefined) {
    return (
      <section className="max-w-3xl">
        <FormAlert message={t("groups.detail.loadError")} />
      </section>
    );
  }
  const detail = group.data;
  const needsAttention = attentionCounts(roster.data?.clients ?? []).total > 0;
  const openAdd = () => {
    setClientPickerOpen(true);
  };
  const openClients = (filter: RosterFilter) => {
    setClientsFilter(filter);
    onTabChange("clients");
  };

  return (
    <section className="max-w-6xl pb-24 sm:pb-16">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{detail.name}</h1>
        {detail.disciplines.length > 0 && (
          <ul className="flex flex-wrap gap-1.5">
            {detail.disciplines.map((discipline) => (
              <li
                key={discipline.id}
                className="rounded-full border bg-card px-2.5 py-0.5 text-xs font-medium text-muted-foreground"
              >
                {discipline.name}
              </li>
            ))}
          </ul>
        )}
        <div className="ml-auto flex items-center gap-2">
          <Button className="max-sm:hidden" onClick={openAdd}>
            <PlusIcon aria-hidden />
            {t("groups.detail.addClients")}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" aria-label={t("groups.detail.more")}>
                <MoreHorizontalIcon aria-hidden />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                onSelect={() => {
                  setRenameOpen(true);
                }}
              >
                {t("groups.detail.rename")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() => {
                  setScheduleOpen(true);
                }}
              >
                {t("groups.detail.editSchedule")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() => {
                  setPicker("disciplines");
                }}
              >
                {t("groups.detail.disciplinesTitle")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() => {
                  setPicker("employees");
                }}
              >
                {t("groups.detail.employeesTitle")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <Tabs
        value={tab}
        onValueChange={(value) => {
          const next = GROUP_TABS.find((candidate) => candidate === value);
          if (next !== undefined) {
            onTabChange(next);
          }
        }}
        className="gap-4"
      >
        <div className="sticky top-14 z-30 -mx-4 overflow-x-auto border-b bg-background px-4 [scrollbar-width:none] sm:static sm:mx-0 sm:px-0">
          <TabsList variant="line" className="h-11">
            {GROUP_TABS.map((value) => (
              <TabsTrigger key={value} value={value} className="flex-none px-3">
                {t(`groups.tabs.${value}`)}
                {value === "clients" && needsAttention && (
                  <span className="size-1.5 rounded-full bg-amber-500">
                    <span className="sr-only">{t("groups.tabs.attentionMark")}</span>
                  </span>
                )}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>

        <TabsContent value="overview">
          <GroupOverviewTab
            api={api}
            branchId={branchId}
            groupId={groupId}
            detail={detail}
            roster={roster.data?.clients}
            onOpenClients={openClients}
            onOpenJournal={() => {
              onTabChange("journal");
            }}
            onEditSchedule={() => {
              setScheduleOpen(true);
            }}
            onEditEmployees={() => {
              setPicker("employees");
            }}
          />
        </TabsContent>
        <TabsContent value="clients">
          <GroupClientsTab
            api={api}
            branchId={branchId}
            roster={roster}
            filter={clientsFilter}
            onFilterChange={setClientsFilter}
            onAdd={openAdd}
            onRemove={(clientId) => {
              void removeClient(clientId);
            }}
            onChanged={() => {
              void refreshRoster();
            }}
          />
        </TabsContent>
        <TabsContent value="memberships">
          <GroupMembershipsTab roster={roster} />
        </TabsContent>
        <TabsContent value="sessions">
          <GroupSessionsSection api={api} branchId={branchId} groupId={groupId} />
        </TabsContent>
        <TabsContent value="journal">
          <GroupJournalTab api={api} branchId={branchId} groupId={groupId} roster={roster} />
        </TabsContent>
      </Tabs>

      <div className="fixed inset-x-0 bottom-0 z-30 border-t bg-background/95 p-3 backdrop-blur sm:hidden">
        <Button className="w-full" onClick={openAdd}>
          <PlusIcon aria-hidden />
          {t("groups.detail.addClients")}
        </Button>
      </div>

      <ChecklistSheet
        open={picker === "disciplines"}
        onOpenChange={(open) => {
          setPicker(open ? "disciplines" : null);
        }}
        title={t("groups.picker.disciplinesTitle")}
        items={disciplines.data ?? []}
        selected={detail.disciplines.map((d) => d.id)}
        emptyText={t("groups.picker.empty")}
        onSubmit={saveDisciplines}
      />
      <ChecklistSheet
        open={picker === "employees"}
        onOpenChange={(open) => {
          setPicker(open ? "employees" : null);
        }}
        title={t("groups.picker.employeesTitle")}
        items={(employees.data ?? []).map(withAvatar)}
        selected={detail.employees.map((e) => e.id)}
        emptyText={t("groups.picker.empty")}
        onSubmit={saveEmployees}
      />

      <ClientPickerSheet
        api={api}
        branchId={branchId}
        open={clientPickerOpen}
        onOpenChange={setClientPickerOpen}
        title={t("groups.detail.addClients")}
        mode="multiple"
        unavailable={
          new Map(
            (roster.data?.clients ?? []).map((client) => [
              client.id,
              t("groups.detail.alreadyInGroup"),
            ]),
          )
        }
        submitLabel={t("action.add")}
        onSubmit={addClients}
      />

      <GroupRenameSheet api={api} open={renameOpen} onOpenChange={setRenameOpen} group={detail} />

      <GroupScheduleSheet
        open={scheduleOpen}
        onOpenChange={setScheduleOpen}
        initialCards={slotsToCards(detail.schedule)}
        scheduleChangeAt={detail.scheduleChangeAt}
        halls={halls.data ?? []}
        onSave={saveSchedule}
      />
    </section>
  );
}
