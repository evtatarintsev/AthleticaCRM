import { QueryClientProvider, useQuery } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { createApiClient, type ApiClientOptions } from "@/api/client";
import { BranchIdSchema, HallIdSchema, type BranchId } from "@/api/generated/contracts";
import { apiErrorOf } from "./apiFailure";
import { apiQuery, createQueryClient } from "./queries";

const center = BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000001");
const north = BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000002");
const hallId = HallIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000003");

/** Клиент API, чей ответ на `halls/list` задаёт [respond]. */
function apiWith(respond: () => Promise<Response>) {
  const fetch = vi.fn<ApiClientOptions["fetch"]>(respond);
  const api = createApiClient({
    fetch,
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  return { api, fetch };
}

/** JSON-ответ со списком из одного зала [name]. */
function halls(name: string): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify({ halls: [{ id: hallId, name }] }), {
      headers: { "Content-Type": "application/json" },
    }),
  );
}

describe("apiQuery", () => {
  it("после смены филиала запрашивает эндпоинт заново и не отдаёт данные прежнего филиала", async () => {
    const responses = [halls("Центральный зал"), halls("Северный зал")];
    const { api, fetch } = apiWith(() => responses.shift() ?? halls("лишний"));
    const queryClient = createQueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result, rerender } = renderHook(
      ({ branch }: { branch: BranchId }) => useQuery(apiQuery(api, branch, "halls/list")),
      { wrapper, initialProps: { branch: center } },
    );
    await waitFor(() => {
      expect(result.current.data?.halls[0]?.name).toBe("Центральный зал");
    });

    rerender({ branch: north });
    expect(result.current.data).toBeUndefined();
    await waitFor(() => {
      expect(result.current.data?.halls[0]?.name).toBe("Северный зал");
    });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("ошибка API доступна запросу как типизированная ApiError без повторов", async () => {
    const { api, fetch } = apiWith(() =>
      Promise.resolve(
        new Response(JSON.stringify({ code: "FORBIDDEN", message: "Нет прав", fields: null }), {
          status: 403,
        }),
      ),
    );
    const queryClient = createQueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(() => useQuery(apiQuery(api, center, "halls/list")), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    const error = result.current.error;
    expect(error === null ? null : apiErrorOf(error)).toEqual({
      kind: "business",
      status: 403,
      code: "FORBIDDEN",
      message: "Нет прав",
    });
    expect(fetch).toHaveBeenCalledOnce();
  });
});
