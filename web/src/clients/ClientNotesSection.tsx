import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilIcon, TrashIcon } from "lucide-react";
import { useEffect, useId, useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  ClientId,
  ClientNoteId,
  ClientNoteSchema,
  ClientNotesListResponse,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { manualField } from "@/forms/field";
import { FormAlert } from "@/forms/FormAlert";
import { TextAreaField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiErrorOf, unwrap } from "@/query/apiFailure";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useEditSheet } from "@/ui/editSheetContext";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { BlockStatus, ClientCardBlock, ShowAllButton } from "./card/ClientCardBlock";

/** Максимальная длина текста заметки; совпадает с ограничением сервера. */
const MAX_NOTE_LENGTH = 2000;

/** Блок заметок клиента: добавление, лента, правка своих заметок, удаление с подтверждением. */
export function ClientNotesSection({
  api,
  branchId,
  clientId,
  currentEmployeeId,
  limit,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
  readonly currentEmployeeId: string;
  readonly limit: number;
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const { reportGuard } = useEditSheet();
  const notesQuery = apiQuery(api, branchId, "clients/notes/list", { clientId });
  const notes = useQuery(notesQuery);

  const fieldId = useId();
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<ClientNoteId | null>(null);
  const [noteToDelete, setNoteToDelete] = useState<ClientNoteSchema | null>(null);
  const [showAll, setShowAll] = useState(false);
  const original = notes.data?.notes.find((note) => note.id === editingId)?.text ?? "";

  const applyResponse = (response: ClientNotesListResponse) => {
    queryClient.setQueryData(notesQuery.queryKey, response);
  };

  const save = useMutation({
    mutationFn: async (text: string) =>
      unwrap(
        editingId === null
          ? await api.call("clients/notes/add", { clientId, text })
          : await api.call("clients/notes/edit", { noteId: editingId, text }),
      ),
    onSuccess: (response) => {
      applyResponse(response);
      cancelEdit();
    },
  });
  const submitting = save.isPending;
  useEffect(() => {
    reportGuard({ dirty: draft !== original, submitting });
  }, [draft, original, submitting, reportGuard]);

  const cancelEdit = () => {
    setEditingId(null);
    setDraft("");
    save.reset();
  };

  const submit = () => {
    const text = draft.trim();
    if (text !== "" && text.length <= MAX_NOTE_LENGTH) {
      save.mutate(text);
    }
  };

  const deleteNote = async (note: ClientNoteSchema) => {
    const result = await api.call("clients/notes/delete", { noteId: note.id });
    if (result.ok) {
      applyResponse(result.value);
      if (editingId === note.id) {
        cancelEdit();
      }
    }
  };

  const count = notes.data?.notes.length ?? 0;
  const shown = showAll ? (notes.data?.notes ?? []) : (notes.data?.notes ?? []).slice(0, limit);

  return (
    <ClientCardBlock
      title={t("clients.detail.notes")}
      count={count}
      footer={
        <ShowAllButton
          total={count}
          limit={limit}
          showAll={showAll}
          onToggle={() => {
            setShowAll(!showAll);
          }}
        />
      }
    >
      <div className="space-y-2 border-b px-4 py-3">
        {save.error !== null && <FormAlert message={apiErrorMessage(t, apiErrorOf(save.error))} />}
        <TextAreaField
          field={manualField(fieldId, draft, setDraft)}
          label={t("clients.detail.noteFieldLabel")}
          rows={2}
        />
        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            disabled={submitting || draft.trim() === ""}
            aria-busy={submitting}
            onClick={submit}
          >
            {editingId === null ? t("action.add") : t("action.save")}
          </Button>
          {editingId !== null && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={submitting}
              onClick={cancelEdit}
            >
              {t("action.cancel")}
            </Button>
          )}
        </div>
      </div>

      <BlockStatus
        isPending={notes.isPending}
        isError={notes.isError}
        isEmpty={count === 0}
        empty={t("clients.detail.noteEmpty")}
      />
      {shown.length > 0 && (
        <ul className="divide-y">
          {shown.map((note) => (
            <li key={note.id} className="space-y-1 px-4 py-2">
              <p className="text-sm break-words whitespace-pre-wrap">{note.text}</p>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium">{note.author.name}</span>
                <span>{format.dateTime(note.createdAt)}</span>
                {note.updatedAt !== null && <span>{t("clients.detail.noteEdited")}</span>}
                {note.author.id === currentEmployeeId && (
                  <span className="ml-auto flex gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("clients.detail.noteEditAria")}
                      onClick={() => {
                        setEditingId(note.id);
                        setDraft(note.text);
                        save.reset();
                      }}
                    >
                      <PencilIcon aria-hidden />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("clients.detail.noteDeleteAria")}
                      onClick={() => {
                        setNoteToDelete(note);
                      }}
                    >
                      <TrashIcon aria-hidden className="text-destructive" />
                    </Button>
                  </span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {noteToDelete !== null && (
        <ConfirmDialog
          open
          onOpenChange={(open) => {
            if (!open) {
              setNoteToDelete(null);
            }
          }}
          title={t("clients.detail.noteDeleteTitle")}
          description={t("clients.detail.noteDeleteMessage")}
          confirmLabel={t("action.delete")}
          onConfirm={() => deleteNote(noteToDelete)}
        />
      )}
    </ClientCardBlock>
  );
}
