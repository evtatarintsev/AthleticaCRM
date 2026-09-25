import { useQuery } from "@tanstack/react-query";
import type { ApiClient } from "@/api/client";
import {
  BranchIdSchema,
  DisciplineIdSchema,
  HallIdSchema,
  LeadSourceIdSchema,
  type BranchId,
  type DisciplineId,
  type HallId,
  type LeadSourceId,
} from "@/api/generated/contracts";
import { uuidv7 } from "@/lib/uuid";
import { apiQuery, myBranchesQuery } from "@/query/queries";
import type { DirectoryDefinition } from "./directory/DirectoryPage";

/** Залы филиала [branchId]. */
function useHalls(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "halls/list"), select: (r) => r.halls });
}

/** Дисциплины филиала [branchId]. */
function useDisciplines(api: ApiClient, branchId: BranchId) {
  return useQuery({
    ...apiQuery(api, branchId, "disciplines/list"),
    select: (r) => r.disciplines,
  });
}

/** Источники клиентов филиала [branchId]. */
function useLeadSources(api: ApiClient, branchId: BranchId) {
  return useQuery({
    ...apiQuery(api, branchId, "lead-sources/list"),
    select: (r) => r.leadSources,
  });
}

/** Филиалы организации; от текущего филиала не зависят, но ключ кэша, как у всех, с ним. */
function useBranches(api: ApiClient, branchId: BranchId) {
  return useQuery({ ...apiQuery(api, branchId, "branches/list"), select: (r) => r.branches });
}

/** Справочник залов. */
export const halls: DirectoryDefinition<HallId> = {
  title: "halls.title",
  createTitle: "halls.create",
  editTitle: "halls.edit",
  listPath: "halls/list",
  useItems: useHalls,
  newId: () => HallIdSchema.parse(uuidv7()),
  create: (api, item) => api.call("halls/create", item),
  update: (api, item) => api.call("halls/update", item),
  remove: (api, ids) => api.call("halls/delete", { ids }),
};

/** Справочник дисциплин. */
export const disciplines: DirectoryDefinition<DisciplineId> = {
  title: "disciplines.title",
  createTitle: "disciplines.create",
  editTitle: "disciplines.edit",
  listPath: "disciplines/list",
  useItems: useDisciplines,
  newId: () => DisciplineIdSchema.parse(uuidv7()),
  create: (api, item) => api.call("disciplines/create", item),
  update: (api, item) => api.call("disciplines/update", item),
  remove: (api, ids) => api.call("disciplines/delete", { ids }),
};

/** Справочник источников клиентов. */
export const leadSources: DirectoryDefinition<LeadSourceId> = {
  title: "leadSources.title",
  createTitle: "leadSources.create",
  editTitle: "leadSources.edit",
  listPath: "lead-sources/list",
  useItems: useLeadSources,
  newId: () => LeadSourceIdSchema.parse(uuidv7()),
  create: (api, item) => api.call("lead-sources/create", item),
  update: (api, item) => api.call("lead-sources/update", item),
  remove: (api, ids) => api.call("lead-sources/delete", { ids }),
};

/** Справочник филиалов; после изменений перечитывается и список филиалов в меню аккаунта. */
export const branches: DirectoryDefinition<BranchId> = {
  title: "branches.title",
  createTitle: "branches.create",
  editTitle: "branches.edit",
  listPath: "branches/list",
  useItems: useBranches,
  newId: () => BranchIdSchema.parse(uuidv7()),
  create: (api, item) => api.call("branches/create", item),
  update: (api, item) => api.call("branches/update", item),
  remove: (api, ids) => api.call("branches/delete", { ids }),
  alsoInvalidates: [myBranchesQuery.key],
};
