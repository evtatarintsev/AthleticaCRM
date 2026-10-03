import { describe, expect, it } from "vitest";
import { TaskIdSchema } from "@/api/generated/contracts";
import {
  panelOf,
  searchOf,
  TaskListSearchSchema,
  taskListFilters,
  withPanel,
} from "./taskListSearch";

const taskId = TaskIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000501");

describe("search-параметры списка задач", () => {
  it("некорректный идентификатор задачи сбрасывается, фильтры остаются", () => {
    const search = TaskListSearchSchema.parse({ task: "not-a-uuid", statuses: ["PENDING"] });

    expect(search.task).toBeUndefined();
    expect(search.statuses).toEqual(["PENDING"]);
    expect(panelOf(search)).toBeNull();
  });

  it("карточка важнее создания", () => {
    const search = TaskListSearchSchema.parse({ task: taskId, create: true });

    expect(panelOf(search)).toEqual({ kind: "task", taskId });
  });

  it("фильтры не знают об открытой панели", () => {
    const search = TaskListSearchSchema.parse({ task: taskId, create: true, onlyMine: true });

    expect(searchOf(taskListFilters(search))).toEqual({ onlyMine: true });
  });

  it("открытие и закрытие панели не меняет фильтры", () => {
    const search = TaskListSearchSchema.parse({ q: "звонок", create: true });

    const opened = withPanel(search, { kind: "task", taskId });
    expect(opened).toEqual({ q: "звонок", task: taskId });
    expect(withPanel(opened, null)).toEqual({ q: "звонок" });
  });
});
