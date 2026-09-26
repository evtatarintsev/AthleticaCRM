import { useQuery, useQueryClient } from "@tanstack/react-query";
import { XIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientGroup, ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";

/** Группы клиента: чипы с удалением и добавление в новую группу через шторку выбора. */
export function ClientGroupsSection({
  api,
  branchId,
  clientId,
  groups,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly groups: readonly ClientGroup[];
  readonly onChanged: () => void;
}) {
  const { t } = useI18n();
  const [sheetOpen, setSheetOpen] = useState(false);

  const removeGroup = async (groupId: ClientGroup["id"]) => {
    const result = await api.call("clients/remove-from-group", { clientIds: [clientId], groupId });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    onChanged();
  };

  return (
    <div className="space-y-3 rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-base font-semibold">{t("clients.column.groups")}</h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => {
            setSheetOpen(true);
          }}
        >
          {t("clients.detail.addToGroup")}
        </Button>
      </div>

      {groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("clients.detail.noGroups")}</p>
      ) : (
        <div className="flex flex-wrap gap-1">
          {groups.map((group) => (
            <span
              key={group.id}
              className="flex items-center gap-1 rounded-full border bg-secondary px-2 py-0.5 text-xs text-secondary-foreground"
            >
              {group.name}
              <button
                type="button"
                aria-label={t("clients.detail.removeFromGroupAria", { name: group.name })}
                onClick={() => {
                  void removeGroup(group.id);
                }}
                className="rounded-full hover:text-destructive"
              >
                <XIcon aria-hidden className="size-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      <ClientAddToGroupSheet
        api={api}
        branchId={branchId}
        clientId={clientId}
        existingGroupIds={new Set(groups.map((group) => group.id))}
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        onAdded={() => {
          setSheetOpen(false);
          onChanged();
        }}
      />
    </div>
  );
}

/** Шторка выбора группы для добавления клиента [clientId]; уже добавленные группы скрыты. */
function ClientAddToGroupSheet({
  api,
  branchId,
  clientId,
  existingGroupIds,
  open,
  onOpenChange,
  onAdded,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly existingGroupIds: ReadonlySet<ClientGroup["id"]>;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly onAdded: () => void;
}) {
  const { t } = useI18n();
  const groupsQuery = apiQuery(api, branchId, "groups/list-for-select");
  const groups = useQuery({ ...groupsQuery, enabled: open });
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [addingId, setAddingId] = useState<ClientGroup["id"] | null>(null);

  const available = useMemo(
    () =>
      (groups.data ?? []).filter(
        (group) =>
          !existingGroupIds.has(group.id) &&
          (query.trim() === "" || group.name.toLowerCase().includes(query.trim().toLowerCase())),
      ),
    [groups.data, existingGroupIds, query],
  );

  const addToGroup = async (groupId: ClientGroup["id"]) => {
    setAddingId(groupId);
    const result = await api.call("clients/add-to-group", { clientIds: [clientId], groupId });
    setAddingId(null);
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] });
    setQuery("");
    onAdded();
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[85vh] overflow-y-auto"
        closeLabel={t("action.close")}
      >
        <SheetHeader>
          <SheetTitle>{t("clients.detail.addToGroup")}</SheetTitle>
          <SheetDescription className="sr-only">{t("clients.detail.addToGroup")}</SheetDescription>
        </SheetHeader>
        <div className="space-y-3 px-4 pb-4">
          <Input
            type="search"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
            }}
            placeholder={t("clients.detail.searchGroup")}
            aria-label={t("clients.detail.searchGroup")}
          />
          {groups.isError && (
            <p className="text-sm text-destructive">{t("clients.detail.loadError")}</p>
          )}
          {groups.data !== undefined &&
            (available.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">{t("groups.empty")}</p>
            ) : (
              <ul className="max-h-96 divide-y overflow-y-auto">
                {available.map((group) => (
                  <li key={group.id}>
                    <button
                      type="button"
                      disabled={addingId !== null}
                      onClick={() => {
                        void addToGroup(group.id);
                      }}
                      className="w-full px-1 py-3 text-left text-sm hover:bg-accent disabled:opacity-50"
                    >
                      {group.name}
                    </button>
                  </li>
                ))}
              </ul>
            ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}
