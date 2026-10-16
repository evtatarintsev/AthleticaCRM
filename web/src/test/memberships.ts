import {
  LocalDateSchema,
  MembershipSchemaSchema,
  type MembershipSchema,
  type MembershipStateSchema,
} from "@/api/generated/contracts";

/** Параметры тестового абонемента; не указанные берутся по умолчанию. */
export interface TestMembership {
  readonly id?: string;
  readonly name?: string;
  readonly sessionsTotal?: number | null;
  readonly sessionsRemaining?: number | null;
  readonly activationMode?: MembershipSchema["activationMode"];
  readonly activationDeadline?: string | null;
  readonly closeMode?: MembershipSchema["closeMode"];
  readonly state: unknown;
}

/** Абонемент в форме ответа сервера `memberships/list` с состоянием [params].state. */
export function testMembership(params: TestMembership): MembershipSchema {
  return MembershipSchemaSchema.parse({
    id: params.id ?? "0199a0b2-7c3e-7d2a-9f10-000000000a01",
    name: params.name ?? "8 занятий",
    tariffPlanId: null,
    sessionsTotal: params.sessionsTotal === undefined ? 8 : params.sessionsTotal,
    sessionsRemaining: params.sessionsRemaining === undefined ? 8 : params.sessionsRemaining,
    durationValue: 1,
    durationUnit: "MONTHS",
    price: { minorUnits: 400000, currency: "RUB" },
    activationMode: params.activationMode ?? "IMMEDIATE",
    activationDeadline: params.activationDeadline ?? null,
    closeMode: params.closeMode ?? "MANUAL",
    issuedOn: "2026-09-01",
    state: params.state,
  });
}

/** Состояние «Активный» с выводимыми свойствами; по умолчанию «в сроке» без признаков. */
export function activeState(
  startDate: string,
  endDate: string,
  props: {
    readonly termPosition?: "NOT_STARTED" | "IN_TERM" | "EXPIRED";
    readonly endingSoon?: readonly ("BY_DATE" | "BY_SESSIONS")[];
    readonly closePending?: boolean;
  } = {},
): MembershipStateSchema {
  return {
    type: "active",
    startDate: LocalDateSchema.parse(startDate),
    endDate: LocalDateSchema.parse(endDate),
    termPosition: props.termPosition ?? "IN_TERM",
    endingSoon: props.endingSoon ?? [],
    closePending: props.closePending ?? false,
  };
}
