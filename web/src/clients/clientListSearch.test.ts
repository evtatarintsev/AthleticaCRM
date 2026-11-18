import { describe, expect, it } from "vitest";
import { LocalDateSchema } from "@/api/generated/contracts";
import { birthdaysShowAllSearch } from "@/home/dashboardSettings";
import {
  birthdayRange,
  clientListFilters,
  clientListRequest,
  ClientListSearchSchema,
  searchOf,
} from "./clientListSearch";

const today = LocalDateSchema.parse("2026-11-18");

describe("дни рождения в адресе списка клиентов", () => {
  it.each([
    ["TODAY", "today", "2026-11-18"],
    ["TOMORROW", "todayTomorrow", "2026-11-19"],
    ["WEEK", "weekAhead", "2026-11-25"],
  ] as const)("%s передаёт тот же период в полный список", (window, birthday, to) => {
    const search = birthdaysShowAllSearch(window);
    expect(search).toEqual({ birthday, sort: "nextBirthday" });
    const filters = clientListFilters(ClientListSearchSchema.parse(search));
    expect(clientListFilters(ClientListSearchSchema.parse(searchOf(filters)))).toEqual(filters);
    expect(clientListRequest(filters, today, 50, 0)).toEqual({
      birthday: { from: today, to },
      limit: 50,
      offset: 0,
      sort: [
        { field: "NEXT_BIRTHDAY", direction: "Asc" },
        { field: "NAME", direction: "Asc" },
      ],
    });
  });

  it("сохраняет прежние границы самостоятельных фильтров", () => {
    expect(birthdayRange("tomorrow", today)).toEqual({ from: "2026-11-19", to: "2026-11-19" });
    expect(birthdayRange("week", today)).toEqual({ from: today, to: "2026-11-24" });
    expect(
      clientListRequest(clientListFilters({ sort: "birthday", dir: "desc" }), today, 50, 0),
    ).toMatchObject({ sortField: "BIRTHDAY", sortDirection: "Desc" });
  });

  it("нормализует направление и сбрасывает ближайший ДР при удалённом фильтре", () => {
    const filters = clientListFilters({
      birthday: "todayTomorrow",
      sort: "nextBirthday",
      dir: "desc",
    });
    expect(filters.dir).toBe("asc");
    expect(searchOf({ ...filters, birthday: "none" })).toEqual({});
    expect(clientListFilters({ sort: "nextBirthday", dir: "desc" })).toMatchObject({
      sort: "name",
      dir: "asc",
    });
    expect(clientListRequest({ ...filters, birthday: "none" }, today, 50, 0)).toEqual({
      limit: 50,
      offset: 0,
      sortField: "NAME",
      sortDirection: "Asc",
    });
  });

  it("восстанавливает обычный порядок при некорректном периоде в адресе", () => {
    expect(
      clientListFilters(
        ClientListSearchSchema.parse({ birthday: "invalid", sort: "nextBirthday" }),
      ),
    ).toMatchObject({ birthday: "none", sort: "name", dir: "asc" });
  });
});
