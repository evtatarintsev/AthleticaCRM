import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilIcon, PlusIcon, TrashIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import {
  ChannelIntegrationIdSchema,
  type ChannelIntegrationSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { PageHeader } from "@/ui/PageHeader";
import { ChannelDialog, type ChannelTerms } from "./ChannelDialog";

/** Что редактируется сейчас: ничего, новый канал или существующий. */
type Editing =
  | { readonly kind: "none" }
  | { readonly kind: "create" }
  | { readonly kind: "edit"; readonly channel: ChannelIntegrationSchema };

/** Каналы связи (паритет с `ChannelsScreen` KMP-клиента): список, создание, изменение, удаление. */
export function ChannelsPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const channelsQuery = apiQuery(api, branchId, "channels/list");
  const channels = useQuery(channelsQuery);
  const [editing, setEditing] = useState<Editing>({ kind: "none" });
  const [deleting, setDeleting] = useState<ChannelIntegrationSchema | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: channelsQuery.queryKey });

  const save = async (
    terms: ChannelTerms,
    channel: ChannelIntegrationSchema | null,
  ): Promise<string | null> => {
    const result =
      channel === null
        ? await api.call("channels/create", {
            id: ChannelIntegrationIdSchema.parse(uuidv7()),
            name: terms.name,
            config: terms.config,
          })
        : await api.call("channels/update", { id: channel.id, ...terms });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    return null;
  };

  const remove = async (channel: ChannelIntegrationSchema): Promise<void> => {
    const result = await api.call("channels/delete", { id: channel.id });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await refresh();
  };

  return (
    <section className="max-w-2xl">
      <PageHeader
        title={t("channels.title")}
        actions={
          <Button
            onClick={() => {
              setEditing({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("channels.create")}
          </Button>
        }
      />
      {channels.isError && <FormAlert message={t("directory.loadError")} />}
      {channels.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      )}
      {channels.data?.channels.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">{t("channels.empty")}</p>
      )}
      {channels.data !== undefined && channels.data.channels.length > 0 && (
        <ul className="divide-y rounded-lg border bg-card">
          {channels.data.channels.map((channel) => (
            <li key={channel.id} className="flex items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{channel.name}</p>
                <p className="truncate text-sm text-muted-foreground">
                  {t(`channels.provider.${channel.config.type}`)}
                  {!channel.enabled && ` · ${t("channels.disabled")}`}
                </p>
              </div>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("channels.editChannel", { name: channel.name })}
                onClick={() => {
                  setEditing({ kind: "edit", channel });
                }}
              >
                <PencilIcon aria-hidden />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("channels.deleteChannel", { name: channel.name })}
                onClick={() => {
                  setDeleting(channel);
                }}
              >
                <TrashIcon aria-hidden className="text-destructive" />
              </Button>
            </li>
          ))}
        </ul>
      )}
      {editing.kind !== "none" && (
        <ChannelDialog
          title={editing.kind === "create" ? t("channels.create") : t("channels.edit")}
          initial={editing.kind === "edit" ? editing.channel : null}
          onSave={(terms) => save(terms, editing.kind === "edit" ? editing.channel : null)}
          onClose={() => {
            setEditing({ kind: "none" });
          }}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleting(null);
          }
        }}
        title={t("channels.deleteTitle", { name: deleting?.name ?? "" })}
        description={t("channels.deleteText")}
        confirmLabel={t("action.delete")}
        onConfirm={async () => {
          if (deleting !== null) {
            await remove(deleting);
          }
          setDeleting(null);
        }}
      />
    </section>
  );
}
