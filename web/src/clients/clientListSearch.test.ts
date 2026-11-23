import { describe, expect, it } from "vitest";
import { LocalDateSchema } from "@/api/generated/contracts";
import { birthdaysShowAllSearch } from "@/home/dashboardSettings";
import {
  birthdayRange,
  clearedFilters,
  clientListEmptyState,
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
  });

  it("сортировки по дате рождения нет: ?sort=birthday из адреса — сортировка по имени", () => {
    const filters = clientListFilters(ClientListSearchSchema.parse({ sort: "birthday" }));
    expect(filters.sort).toBe("name");
    expect(clientListRequest(filters, today, 50, 0)).toMatchObject({
      sortField: "NAME",
      sortDirection: "Asc",
    });
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

describe("пустое состояние списка клиентов", () => {
  it.each([
    ["вид «Все» без поиска", {}, "no-clients"],
    ["пустой вид «Должники»", { debt: true }, "view-empty"],
    ["пустой вид «Без группы»", { noGroup: true }, "view-empty"],
    ["пустой вид «Архив»", { archived: true }, "view-empty"],
    ["вид «Без группы» и фильтр «Женский»", { noGroup: true, gender: "female" }, "no-results"],
    ["поиск «зз»", { q: "зз" }, "no-results"],
    ["поиск в виде «Должники»", { q: "зз", debt: true }, "no-results"],
  ] as const)("%s", (_, search, expected) => {
    expect(clientListEmptyState(clientListFilters(search))).toBe(expected);
  });
});

describe("сброс фильтров списка клиентов", () => {
  it("оставляет в адресе только сортировку", () => {
    const filters = clientListFilters({
      q: "зз",
      gender: "female",
      noGroup: true,
      birthday: "today",
      archived: true,
      sort: "balance",
      dir: "desc",
    });
    expect(searchOf(clearedFilters(filters))).toEqual({ sort: "balance", dir: "desc" });
  });
});
