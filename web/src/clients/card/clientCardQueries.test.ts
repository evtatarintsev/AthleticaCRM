import { expect, it } from "vitest";
import { createApiClient } from "@/api/client";
import { BranchIdSchema, ClientIdSchema, TaskListRequestSchema } from "@/api/generated/contracts";
import { createQueryClient } from "@/query/queries";
import { fakeServer, json } from "@/test/app";
import { clientTasksQuery, clientTaskSummaryQuery } from "./clientCardQueries";

it("uses server totals beyond the page and refreshes both summaries with the task list", async () => {
  const branchId = BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000003");
  const clientId = ClientIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000501");
  let completed = false;
  const server = fakeServer({
    "tasks/list": ({ body }) => {
      const request = TaskListRequestSchema.parse(body);
      return json({
        tasks: [],
        total:
          request.statuses === undefined
            ? 80
            : request.dueDateTo != null
              ? completed
                ? 11
                : 12
              : completed
                ? 59
                : 60,
      });
    },
  });
  const api = createApiClient({
    fetch: server.fetch,
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  const cache = createQueryClient();
  const list = clientTasksQuery(api, branchId, clientId);
  const total = clientTaskSummaryQuery(api, branchId, clientId, false);
  const overdue = clientTaskSummaryQuery(api, branchId, clientId, true);
  expect((await cache.query(list)).total).toBe(80);
  expect((await cache.query(total)).total).toBe(60);
  expect((await cache.query(overdue)).total).toBe(12);
  completed = true;
  await cache.invalidateQueries({ queryKey: ["api", branchId, "tasks/list"], refetchType: "all" });
  expect(cache.getQueryData(total.queryKey)?.total).toBe(59);
  expect(cache.getQueryData(overdue.queryKey)?.total).toBe(11);
  expect(server.requests).toHaveLength(6);
  for (const request of server.requests.filter(
    (r) => TaskListRequestSchema.parse(r.body).statuses !== undefined,
  )) {
    expect(TaskListRequestSchema.parse(request.body).statuses).toEqual([
      "PENDING",
      "IN_PROGRESS",
      "PAUSED",
    ]);
  }
});
