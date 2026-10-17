import { useQuery } from "@tanstack/react-query";
import { ArrowRightIcon, ListChecksIcon, MessagesSquareIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  InstantSchema,
  type BranchId,
  type ClientId,
  type TaskId,
  type TaskListItemSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { clientTasksQuery } from "./card/clientCardQueries";
import { TaskStatusBadge } from "@/tasks/TaskStatusBadge";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  Pill,
  ShowAllButton,
} from "./card/ClientCardBlock";

/** Сколько последних сообщений показывается в блоке. */
const MESSAGES_SHOWN = 5;

/** Порядок задач: незавершённые раньше завершённых, внутри — по сроку, без срока — в конце. */
function compareTasks(a: TaskListItemSchema, b: TaskListItemSchema): number {
  const doneA = a.status === "COMPLETED";
  const doneB = b.status === "COMPLETED";
  if (doneA !== doneB) {
    return doneA ? 1 : -1;
  }
  return (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999");
}

/**
 * Блок задач клиента: сначала незавершённые по сроку, просроченные выделены.
 * [onCreate] открывает создание задачи, [onOpen] — карточку выбранной задачи.
 */
export function ClientTasksSection({
  api,
  branchId,
  clientId,
  limit,
  onCreate,
  onOpen,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly limit: number;
  readonly onCreate: () => void;
  readonly onOpen: (taskId: TaskId) => void;
}) {
  const { t, format } = useI18n();
  const [showAll, setShowAll] = useState(false);
  const tasks = useQuery(clientTasksQuery(api, branchId, clientId));
  const now = new Date().getTime();
  const items = (tasks.data?.tasks ?? []).toSorted(compareTasks);
  const shown = showAll ? items : items.slice(0, limit);

  return (
    <ClientCardBlock
      icon={<ListChecksIcon aria-hidden />}
      title={t("tasks.title")}
      count={items.length}
      actions={
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t("tasks.create")}
          title={t("tasks.create")}
          onClick={onCreate}
        >
          <PlusIcon aria-hidden />
        </Button>
      }
      footer={
        <ShowAllButton
          total={items.length}
          limit={limit}
          showAll={showAll}
          onToggle={() => {
            setShowAll(!showAll);
          }}
        />
      }
    >
      <BlockStatus
        isPending={tasks.isPending}
        isError={tasks.isError}
        isEmpty={items.length === 0}
        empty={t("clientCard.tasksEmpty")}
      />
      {shown.length > 0 && (
        <ul className="divide-y">
          {shown.map((task) => {
            const overdue =
              task.status !== "COMPLETED" &&
              task.dueDate !== null &&
              Date.parse(task.dueDate) < now;
            return (
              <BlockRow key={task.id} aside={<TaskStatusBadge status={task.status} />}>
                <button
                  type="button"
                  onClick={() => {
                    onOpen(task.id);
                  }}
                  className={cn(
                    "text-left font-medium hover:underline",
                    task.status === "COMPLETED" && "text-muted-foreground line-through",
                  )}
                >
                  {task.title}
                </button>
                <BlockSub>
                  {task.dueDate !== null && (
                    <span className={cn(overdue && "font-medium text-destructive")}>
                      {format.dateTime(task.dueDate)}
                    </span>
                  )}
                  {task.dueDate !== null && task.assigneeName !== null && " · "}
                  {task.assigneeName}
                </BlockSub>
              </BlockRow>
            );
          })}
        </ul>
      )}
    </ClientCardBlock>
  );
}

/** Блок сообщений: последние сообщения переписки с клиентом; [onOpen] открывает панель «Чат». */
export function ClientMessagesSection({
  api,
  branchId,
  clientId,
  onOpen,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly onOpen: () => void;
}) {
  const { t, format } = useI18n();
  const conversation = useQuery(apiQuery(api, branchId, "messaging/conversation", { clientId }));
  const messages = conversation.data?.messages ?? [];
  const latest = messages.slice(-MESSAGES_SHOWN).toReversed();
  const now = new Date();

  return (
    <ClientCardBlock
      icon={<MessagesSquareIcon aria-hidden />}
      title={t("clientCard.block.messages")}
      count={messages.length}
      footer={
        <Button variant="ghost" size="sm" onClick={onOpen}>
          {t("clientCard.openConversation")}
          <ArrowRightIcon aria-hidden />
        </Button>
      }
    >
      <BlockStatus
        isPending={conversation.isPending}
        isError={conversation.isError}
        isEmpty={messages.length === 0}
        empty={t("messaging.emptyThread")}
      />
      {latest.length > 0 && (
        <ul className="divide-y">
          {latest.map((message) => {
            const at = InstantSchema.safeParse(message.createdAt);
            return (
              <BlockRow
                key={message.id}
                aside={at.success ? <BlockSub>{format.ago(at.data, now)}</BlockSub> : null}
              >
                <Pill tone={message.type === "inbound" ? "blue" : "gray"}>
                  {t(
                    message.type === "inbound"
                      ? "clientCard.messageIncoming"
                      : "clientCard.messageOutgoing",
                  )}
                </Pill>
                <div className="mt-1 line-clamp-2">{message.body}</div>
              </BlockRow>
            );
          })}
        </ul>
      )}
    </ClientCardBlock>
  );
}
