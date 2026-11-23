import type { APIRequestContext } from "@playwright/test";
import { uuidv7 } from "../../web/src/lib/uuid";
import { call } from "./api";

/** Подготовка данных сценария через API от имени владельца [request]. Возвращает идентификаторы. */

/** Вчерашняя дата в часовом поясе тестовых организаций, `YYYY-MM-DD`: занятие уже прошло. */
export function yesterdayInOrg(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Moscow" }).format(
    new Date(Date.now() - 24 * 60 * 60 * 1000),
  );
}

/** Создаёт дисциплину [name]. */
export async function createDiscipline(request: APIRequestContext, name: string): Promise<string> {
  const id = uuidv7();
  await call(request, "disciplines/create", { id, name });
  return id;
}

/** Создаёт группу [name]. */
export async function createGroup(request: APIRequestContext, name: string): Promise<string> {
  const id = uuidv7();
  await call(request, "groups/create", { id, name });
  return id;
}

/** Создаёт клиента [name]. */
export async function createClient(request: APIRequestContext, name: string): Promise<string> {
  const id = uuidv7();
  await call(request, "clients/create", { id, name, gender: "FEMALE" });
  return id;
}

/** Добавляет клиента [clientId] в группу [groupId]. */
export async function addToGroup(
  request: APIRequestContext,
  clientId: string,
  groupId: string,
): Promise<void> {
  await call(request, "clients/add-to-group", { clientIds: [clientId], groupId });
}

/** Создаёт занятие группы [groupId] на дату [date] в первом зале организации. */
export async function createSession(
  request: APIRequestContext,
  groupId: string,
  date: string,
): Promise<string> {
  const { halls } = await call(request, "halls/list", undefined);
  const hall = halls[0];
  if (hall === undefined) {
    throw new Error("У новой организации нет зала");
  }
  const id = uuidv7();
  await call(request, "sessions/create", {
    id,
    groupId,
    date,
    startTime: "10:00:00",
    endTime: "11:00:00",
    hallId: hall.id,
    employeeIds: [],
  });
  return id;
}

/** Сумма [rubles] в рублях организации. */
export function rub(rubles: number): { minorUnits: number; currency: "RUB" } {
  return { minorUnits: rubles * 100, currency: "RUB" };
}

/** Создаёт тариф [name] на 8 занятий за месяц по цене [price]. */
export async function createTariff(
  request: APIRequestContext,
  name: string,
  price: number,
): Promise<string> {
  const id = uuidv7();
  await call(request, "tariffs/create", {
    id,
    name,
    sessions: 8,
    durationValue: 1,
    durationUnit: "MONTHS",
    price: rub(price),
  });
  return id;
}

/** Создаёт кассу наличных [name]. */
export async function createCashAccount(request: APIRequestContext, name: string): Promise<string> {
  const id = uuidv7();
  await call(request, "finance/cash-accounts/save", { id, name, kind: "CASH" });
  return id;
}

/** Создаёт сотрудника [name] с почтой [email], доступом ко всем филиалам и без дополнительных прав. */
export async function createEmployee(
  request: APIRequestContext,
  name: string,
  email: string,
): Promise<string> {
  const id = uuidv7();
  await call(request, "employees/create", { id, name, email, allBranchesAccess: true });
  return id;
}
