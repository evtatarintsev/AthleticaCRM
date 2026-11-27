import { QueryClientProvider } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import { CoinsIcon, UsersIcon } from "lucide-react";
import { describe, expect, it } from "vitest";
import { createApiClient } from "@/api/client";
import { AuthMeResponseSchema, ClientIdSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { createQueryClient, sessionQuery } from "@/query/queries";
import { appServer, session } from "@/test/app";
import { renderPage } from "@/test/render";
import { ReportPeople, ReportScreen, ReportSection } from "./ReportScreen";
import { ReportSearchSchema } from "./reportSearch";
import { reportById } from "./registry";

/** Экран отчёта «Группы» с тестовыми данными и фильтром «30 дней». */
function renderScreen() {
  const server = appServer({});
  const api = createApiClient({
    fetch: server.fetch,
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  const queryClient = createQueryClient();
  queryClient.setQueryData(
    sessionQuery(api).queryKey,
    AuthMeResponseSchema.parse(session("Иван Петров", null, undefined, ["CAN_VIEW_REPORTS"])),
  );
  const report = reportById("groups");
  if (report === undefined) {
    throw new Error("нет отчёта groups");
  }
  renderPage(
    <QueryClientProvider client={queryClient}>
      <ReportScreen
        api={api}
        report={report}
        search={ReportSearchSchema.parse({ period: "30d" })}
        onSearchChange={() => undefined}
        hero={{
          value: "394",
          caption: "клиентов в группах",
          change: { text: "▲ 9", tone: "good" },
          chart: [{ key: "2026-11", label: "ноя", value: 394, tooltip: "Ноябрь: 394" }],
          chartLabel: "Клиентов в группах по месяцам",
        }}
        tiles={[
          { icon: UsersIcon, label: "Групп", value: "6" },
          { icon: CoinsIcon, label: "Растут", value: "4", note: "+19 клиентов", tone: "good" },
        ]}
        howCalculated="Клиенты с действующим абонементом в группе на последний день периода."
      >
        <ReportSection title="По группам">
          <p>Юниоры У12</p>
        </ReportSection>
        <ReportPeople
          api={api}
          title="Перестали ходить"
          people={[
            {
              clientId: ClientIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-0000000000aa"),
              name: "Артём Новиков",
              avatarId: null,
              caption: "не был 3 недели",
            },
          ]}
        />
      </ReportScreen>
    </QueryClientProvider>,
  );
}

describe("ReportScreen", () => {
  it("рисует плашку, плитки, разбивки, людей и «Как считается»", async () => {
    renderScreen();
    expect(
      await screen.findByRole("heading", { level: 1, name: ru["reports.groups.name"] }),
    ).toBeInTheDocument();
    expect(screen.getByText("394")).toBeInTheDocument();
    expect(screen.getByText("▲ 9")).toHaveAttribute("data-tone", "good");
    expect(screen.getByRole("img", { name: "Клиентов в группах по месяцам" })).toBeInTheDocument();
    expect(screen.getByText("+19 клиентов")).toHaveAttribute("data-tone", "good");
    expect(screen.getByRole("heading", { name: "По группам" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Артём Новиков" })).toHaveAttribute(
      "href",
      "/clients/0199a0b2-7c3e-7d2a-9f10-0000000000aa",
    );
    expect(screen.getByText(/Клиенты с действующим абонементом/)).toBeInTheDocument();
  });

  it("«Все отчёты» ведёт в раздел с теми же фильтрами", async () => {
    renderScreen();
    const back = await screen.findByRole("link", { name: ru["reports.allReports"] });
    expect(back).toHaveAttribute("href", expect.stringContaining("/reports?"));
    expect(back).toHaveAttribute("href", expect.stringContaining("period=30d"));
  });
});
