import { describe, expect, it } from "vitest";
import {
  BranchIdSchema,
  EmployeeIdSchema,
  type EmployeeDetailResponse,
  InstantSchema,
} from "@/api/generated/contracts";
import { employeeFormValuesOf, permissionLists } from "./employeeForm";

const branchId = BranchIdSchema.parse("01900000-0000-7000-8000-000000000001");

const employee: EmployeeDetailResponse = {
  id: EmployeeIdSchema.parse("01900000-0000-7000-8000-000000000002"),
  name: "Иван",
  avatarId: null,
  isOwner: false,
  isActive: true,
  joinedAt: InstantSchema.parse("2026-01-01T00:00:00Z"),
  roles: [{ id: "01900000-0000-7000-8000-000000000003", name: "Тренер" }],
  phoneNo: null,
  email: "ivan@example.com",
  grantedPermissions: ["CAN_VIEW_ALL_TASKS", "CAN_MANAGE_TASKS"],
  revokedPermissions: ["CAN_MANAGE_TASKS", "CAN_VIEW_CLIENT_BALANCE"],
  allBranchesAccess: false,
  branchIds: [branchId],
};

describe("значения формы сотрудника", () => {
  it("собирает значения из карточки, отзыв важнее выдачи", () => {
    expect(employeeFormValuesOf(employee)).toEqual({
      name: "Иван",
      phoneNo: "",
      email: "ivan@example.com",
      avatarId: null,
      roleIds: ["01900000-0000-7000-8000-000000000003"],
      permissions: {
        CAN_VIEW_ALL_TASKS: "grant",
        CAN_MANAGE_TASKS: "revoke",
        CAN_VIEW_CLIENT_BALANCE: "revoke",
      },
      branchIds: [branchId],
    });
  });

  it("раскладывает решения по правам на выданные и отозванные", () => {
    expect(
      permissionLists({ CAN_VIEW_ALL_TASKS: "grant", CAN_MANAGE_ORG_BALANCE: "revoke" }),
    ).toEqual({
      grantedPermissions: ["CAN_VIEW_ALL_TASKS"],
      revokedPermissions: ["CAN_MANAGE_ORG_BALANCE"],
    });
  });

  it("без решений оба списка пусты", () => {
    expect(permissionLists({})).toEqual({ grantedPermissions: [], revokedPermissions: [] });
  });
});
