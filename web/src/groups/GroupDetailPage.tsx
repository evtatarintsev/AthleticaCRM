import { useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PencilIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type {
  DisciplineId,
  EmployeeId,
  GroupId,
  LocalDate,
  ScheduleSlot,
} from "@/api/generated/contracts";
import { Avatar } from "@/ui/Avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { todayLocalDate, WEEK_DAYS } from "@/lib/localDate";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { GroupScheduleDialog } from "./GroupScheduleDialog";
import { cardsToSlotInputs, slotsToCards, type SlotCard } from "./groupSchedule";
import { useGroup, useGroupDisciplines, useGroupEmployees, useGroupHalls } from "./groupsQueries";

/**
 * Карточка группы: дисциплины, расписание, тренеры и клиенты. Дисциплины и тренеры
 * добавляются и убираются на месте — изменение сразу отражается в карточке (7.2).
 */
export function GroupDetailPage({
  api,
  groupId,
}: {
  readonly api: ApiClient;
  readonly groupId: GroupId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const group = useGroup(api, branchId, groupId);
  const disciplines = useGroupDisciplines(api, branchId);
  const employees = useGroupEmployees(api, branchId);
  const halls = useGroupHalls(api, branchId);

  const [picker, setPicker] = useState<"disciplines" | "employees" | null>(null);
  const [scheduleOpen, setScheduleOpen] = useState(false);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] }),
      queryClient.invalidateQueries({
        queryKey: ["api", branchId, "groups/detail", { id: groupId }],
      }),
    ]);

  const saveDisciplines = async (disciplineIds: readonly DisciplineId[]) => {
    const result = await api.call("groups/set-disciplines", { groupId, disciplineIds });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await refresh();
    toast.success(t("groups.detail.disciplinesSaved"));
  };

  const saveEmployees = async (employeeIds: readonly EmployeeId[]) => {
    const result = await api.call("groups/set-employees", { groupId, employeeIds });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await refresh();
    toast.success(t("groups.detail.employeesSaved"));
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

  return (
    <section className="max-w-3xl space-y-6 pb-16">
      <PageHeader
        title={detail.name}
        actions={
          <Button asChild variant="outline">
            <Link to="/groups/$groupId/edit" params={{ groupId }}>
              <PencilIcon aria-hidden />
              {t("groups.detail.editAction")}
            </Link>
          </Button>
        }
      />

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("groups.detail.disciplinesTitle")}
        </h2>
        <ChipsList
          items={detail.disciplines}
          addLabel={t("groups.detail.addDiscipline")}
          onAdd={() => {
            setPicker("disciplines");
          }}
          onRemove={(id) => {
            void saveDisciplines(
              detail.disciplines.map((d) => d.id).filter((current) => current !== id),
            );
          }}
        />
      </section>

      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-medium text-muted-foreground">
            {t("groups.detail.scheduleTitle")}
          </h2>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setScheduleOpen(true);
            }}
          >
            {t("groups.detail.editSchedule")}
          </Button>
        </div>
        <ScheduleSummaryList schedule={detail.schedule} />
        {detail.scheduleChangeAt !== null && (
          <p className="text-xs text-muted-foreground">
            {t("groups.scheduleChangeNote", { date: detail.scheduleChangeAt })}
          </p>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("groups.detail.employeesTitle")}
        </h2>
        <div className="flex flex-wrap gap-2">
          {detail.employees.map((employee) => (
            <span
              key={employee.id}
              className="inline-flex items-center gap-2 rounded-full border bg-accent py-1 pr-3 pl-1 text-xs font-medium"
            >
              <Avatar
                api={api}
                uploadId={employee.avatarId}
                name={employee.name}
                className="size-5"
              />
              {employee.name}
              <button
                type="button"
                aria-label={t("groups.removeChip", { name: employee.name })}
                onClick={() => {
                  void saveEmployees(
                    detail.employees.map((e) => e.id).filter((current) => current !== employee.id),
                  );
                }}
              >
                ×
              </button>
            </span>
          ))}
          <button
            type="button"
            onClick={() => {
              setPicker("employees");
            }}
            className="rounded-full border border-dashed px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
          >
            <PlusIcon aria-hidden className="mr-1 inline size-3" />
            {t("groups.detail.addEmployee")}
          </button>
        </div>
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("groups.detail.clientsTitle")}
        </h2>
        {detail.clients.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("groups.detail.clientsEmpty")}</p>
        ) : (
          <ul className="divide-y rounded-md border">
            {detail.clients.map((client) => (
              <li key={client.id}>
                <Link
                  to="/clients/$clientId"
                  params={{ clientId: client.id }}
                  className="block px-4 py-3 text-sm hover:bg-accent"
                >
                  {client.name}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <MultiSelectPicker
        open={picker === "disciplines"}
        title={t("groups.picker.disciplinesTitle")}
        items={disciplines.data ?? []}
        selected={detail.disciplines.map((d) => d.id)}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "disciplines" : null);
        }}
        onApply={(ids) => {
          void saveDisciplines(ids);
        }}
      />
      <MultiSelectPicker
        open={picker === "employees"}
        title={t("groups.picker.employeesTitle")}
        items={employees.data ?? []}
        selected={detail.employees.map((e) => e.id)}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "employees" : null);
        }}
        onApply={(ids) => {
          void saveEmployees(ids);
        }}
      />

      {scheduleOpen && (
        <GroupScheduleDialog
          initialCards={slotsToCards(detail.schedule)}
          scheduleChangeAt={detail.scheduleChangeAt}
          halls={halls.data ?? []}
          onSave={saveSchedule}
          onClose={() => {
            setScheduleOpen(false);
          }}
        />
      )}
    </section>
  );
}

/** Список чипов [items] с крестиком удаления и кнопкой добавления. */
function ChipsList<Id extends string>({
  items,
  addLabel,
  onAdd,
  onRemove,
}: {
  readonly items: readonly { readonly id: Id; readonly name: string }[];
  readonly addLabel: string;
  readonly onAdd: () => void;
  readonly onRemove: (id: Id) => void;
}) {
  const { t } = useI18n();
  return (
    <div className="flex flex-wrap gap-2">
      {items.map((item) => (
        <span
          key={item.id}
          className="inline-flex items-center gap-1 rounded-full border bg-accent px-3 py-1 text-xs font-medium"
        >
          {item.name}
          <button
            type="button"
            aria-label={t("groups.removeChip", { name: item.name })}
            onClick={() => {
              onRemove(item.id);
            }}
          >
            ×
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={onAdd}
        className="rounded-full border border-dashed px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
      >
        {addLabel}
      </button>
    </div>
  );
}

/** Сводка действующего расписания [schedule] карточками: дни, время, зал. */
function ScheduleSummaryList({ schedule }: { readonly schedule: readonly ScheduleSlot[] }) {
  const { t } = useI18n();
  const cards = slotsToCards(schedule);
  if (cards.length === 0) {
    return <p className="text-sm text-muted-foreground">{t("groups.noSchedule")}</p>;
  }
  return (
    <ul className="space-y-1 text-sm">
      {cards.map((card) => (
        <li key={card.id} className="text-muted-foreground">
          {WEEK_DAYS.filter((day) => card.days.has(day))
            .map((day) => t(`day.${day}`))
            .join(" ")}{" "}
          {card.startAt}–{card.endAt}
        </li>
      ))}
    </ul>
  );
}
