import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilIcon, PlusIcon, TrashIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import {
  ChannelIntegrationIdSchema,
  type ChannelIntegrationId,
  type ChannelIntegrationSchema,
  type ChannelListResponse,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { PageHeader } from "@/ui/PageHeader";
import type { ChannelSave } from "./ChannelForms";
import { ChannelShareButton } from "./ChannelShareButton";
import { ChannelSheet } from "./ChannelSheet";

/** Состояние панели канала: открыта ли она и какой канал в ней (`null` — новый). */
interface SheetState {
  readonly open: boolean;
  readonly channel: ChannelIntegrationSchema | null;
}

/**
 * Каналы связи: список с переключателем включённости и кнопкой «Поделиться», создание и
 * изменение в правой панели, удаление.
 */
export function ChannelsPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const channelsQuery = apiQuery(api, branchId, "channels/list");
  const channels = useQuery(channelsQuery);
  const [sheet, setSheet] = useState<SheetState>({ open: false, channel: null });
  const [deleting, setDeleting] = useState<ChannelIntegrationSchema | null>(null);
  const [toggling, setToggling] = useState<ReadonlySet<ChannelIntegrationId>>(new Set());

  const refresh = () => queryClient.invalidateQueries({ queryKey: channelsQuery.queryKey });

  const save = async (request: ChannelSave): Promise<string | null> => {
    const result =
      request.kind === "create"
        ? await api.call("channels/create", {
            id: ChannelIntegrationIdSchema.parse(uuidv7()),
            name: request.name,
            config: request.config,
          })
        : await api.call("channels/update", {
            id: request.id,
            name: request.name,
            config: request.config,
          });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    return null;
  };

  const setEnabled = (id: ChannelIntegrationId, enabled: boolean) =>
    queryClient.setQueryData<ChannelListResponse>(channelsQuery.queryKey, (data) =>
      data === undefined
        ? data
        : {
            channels: data.channels.map((channel) =>
              channel.id === id ? { ...channel, enabled } : channel,
            ),
          },
    );

  const toggle = async (channel: ChannelIntegrationSchema, enabled: boolean): Promise<void> => {
    setToggling((current) => new Set(current).add(channel.id));
    setEnabled(channel.id, enabled);
    const result = await api.call("channels/set-enabled", { id: channel.id, enabled });
    if (!result.ok) {
      setEnabled(channel.id, channel.enabled);
      toast.error(apiErrorMessage(t, result.error));
    }
    setToggling((current) => {
      const next = new Set(current);
      next.delete(channel.id);
      return next;
    });
    await refresh();
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
              setSheet({ open: true, channel: null });
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
                  {t(`channels.provider.${channel.settings.type}`)}
                </p>
              </div>
              {channel.share !== null && (
                <ChannelShareButton name={channel.name} share={channel.share} />
              )}
              <Switch
                checked={channel.enabled}
                disabled={toggling.has(channel.id)}
                aria-label={t("channels.toggleChannel", { name: channel.name })}
                onCheckedChange={(enabled) => {
                  void toggle(channel, enabled);
                }}
              />
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("channels.editChannel", { name: channel.name })}
                onClick={() => {
                  setSheet({ open: true, channel });
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
      <ChannelSheet
        open={sheet.open}
        onOpenChange={(open) => {
          setSheet((current) => ({ ...current, open }));
        }}
        channel={sheet.channel}
        onSave={save}
      />
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
