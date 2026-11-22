import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { MessageSquareIcon, PlusIcon } from "lucide-react";
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
import { unwrap } from "@/query/apiFailure";
import { clientTasksQuery } from "./card/clientCardQueries";
import { TaskStatusBadge } from "@/tasks/TaskStatusBadge";
import { BlockRow, BlockStatus, BlockSub, ClientCardBlock } from "./card/ClientCardBlock";

/** Сколько последних сообщений показывается в блоке. */
const MESSAGES_SHOWN = 3;

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
  onAll,
  onCreate,
  onOpen,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly onAll?: () => void;
  readonly onCreate: () => void;
  readonly onOpen: (taskId: TaskId) => void;
}) {
  const { t, format } = useI18n();
  const unfinished = onAll !== undefined;
  const count = useQuery({ ...clientTasksQuery(api, branchId, clientId), enabled: unfinished });
  const tasks = useInfiniteQuery({
    queryKey: ["api", branchId, "tasks/list", { clientId, pages: true, unfinished }] as const,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.call("tasks/list", {
          clientId,
          limit: 50,
          offset: pageParam,
          ...(unfinished ? { statuses: ["PENDING", "IN_PROGRESS", "PAUSED"] } : {}),
        }),
      ),
    getNextPageParam: (last, pages) => {
      const loaded = pages.reduce((total, page) => total + page.tasks.length, 0);
      return last.tasks.length > 0 && loaded < last.total ? loaded : undefined;
    },
  });
  const now = new Date().getTime();
  const shown = (tasks.data?.pages.flatMap((page) => page.tasks) ?? []).toSorted(compareTasks);
  const total = tasks.data?.pages[0]?.total ?? 0;

  return (
    <ClientCardBlock
      title={t("tasks.title")}
      count={total}
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
        <>
          {tasks.hasNextPage && (
            <Button
              variant="link"
              size="sm"
              disabled={tasks.isFetchingNextPage}
              onClick={() => {
                void tasks.fetchNextPage();
              }}
            >
              {t("clients.loadMore")}
            </Button>
          )}
          {onAll !== undefined && (
            <Button variant="link" size="sm" onClick={onAll}>
              {t("clientOverview.allTasks")} · {count.data?.total ?? 0}
            </Button>
          )}
        </>
      }
    >
      <BlockStatus
        isPending={tasks.isPending}
        isError={tasks.isError}
        isEmpty={shown.length === 0}
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
                    "max-w-full text-left font-medium break-words hover:underline",
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
      title={t("clientCard.block.messages")}
      count={messages.length}
      actions={
        <Button variant="link" size="sm" onClick={onOpen}>
          <MessageSquareIcon aria-hidden />
          {t("clientCard.openConversation")}
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
              <li key={message.id} className="flex flex-col gap-0.5 px-4 py-2.5 text-sm">
                <span className="flex justify-between gap-2 text-xs text-muted-foreground">
                  <span>
                    {t(
                      message.type === "inbound"
                        ? "clientCard.messageIncoming"
                        : "clientCard.messageOutgoing",
                    )}
                  </span>
                  {at.success && <span>{format.ago(at.data, now)}</span>}
                </span>
                <span
                  className={cn(
                    "truncate",
                    message.type === "inbound" ? "text-foreground" : "text-foreground/80",
                  )}
                >
                  {message.body}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </ClientCardBlock>
  );
}
