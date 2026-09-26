import { useQuery } from "@tanstack/react-query";
import { useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  ClientImportCommitResponse,
  ClientImportParseResponse,
  CustomFieldDefinition,
  Gender,
  ImportTarget,
  LeadSourceDetailResponse,
} from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { clientCustomFieldsQuery } from "@/clients/clientsQueries";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import {
  commitRequest,
  nameColumnCount,
  optionValueToTarget,
  targetToOptionValue,
  type ImportForm,
} from "./importForm";

/** Шаг мастера импорта. */
type Phase =
  | { readonly kind: "upload" }
  | {
      readonly kind: "mapping";
      readonly form: ImportForm;
      readonly parse: ClientImportParseResponse;
    }
  | {
      readonly kind: "preview";
      readonly form: ImportForm;
      readonly parse: ClientImportParseResponse;
      readonly result: ClientImportCommitResponse;
    }
  | { readonly kind: "done"; readonly result: ClientImportCommitResponse };

/**
 * Импорт клиентов из файла (паритет с `ClientImportScreen` KMP-клиента): загрузка файла,
 * сопоставление колонок с полями клиента, проверка без записи, импорт.
 */
export function ClientImportPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const leadSources = useQuery({
    ...apiQuery(api, branchId, "lead-sources/list"),
    select: (r) => r.leadSources,
  });
  const customFields = useQuery(clientCustomFieldsQuery(api, branchId));
  const [phase, setPhase] = useState<Phase>({ kind: "upload" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  const upload = async (file: File): Promise<void> => {
    setBusy(true);
    setError(null);
    const uploaded = await uploadFile(api, file);
    if (!uploaded.ok) {
      setBusy(false);
      setError(apiErrorMessage(t, uploaded.error));
      return;
    }
    const parsed = await api.call("clients/import/parse", { uploadId: uploaded.value.id });
    setBusy(false);
    if (!parsed.ok) {
      setError(apiErrorMessage(t, parsed.error));
      return;
    }
    const columnMapping: Record<string, ImportTarget> = {};
    parsed.value.columns.forEach((column) => {
      columnMapping[column] = { type: "skip" };
    });
    setPhase({
      kind: "mapping",
      parse: parsed.value,
      form: {
        uploadId: uploaded.value.id,
        originalName: parsed.value.originalName,
        columnMapping,
        defaultGender: "FEMALE",
        genderMapping: {},
        leadSourceMapping: {},
        dateFormat: "",
      },
    });
  };

  const runCommit = async (
    form: ImportForm,
    dryRun: boolean,
  ): Promise<ClientImportCommitResponse | null> => {
    setBusy(true);
    setError(null);
    const result = await api.call("clients/import/commit", commitRequest(form, dryRun));
    setBusy(false);
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return null;
    }
    return result.value;
  };

  return (
    <section className="max-w-3xl">
      <PageHeader title={t("import.title")} />
      {error !== null && <FormAlert message={error} />}
      {phase.kind === "upload" && (
        <div className="space-y-3">
          <input
            ref={input}
            type="file"
            accept=".csv,.xlsx,.xls"
            tabIndex={-1}
            aria-hidden
            className="sr-only"
            onChange={(event) => {
              const file = event.target.files?.item(0) ?? null;
              event.target.value = "";
              if (file !== null) {
                void upload(file);
              }
            }}
          />
          <Button
            disabled={busy}
            aria-busy={busy}
            onClick={() => {
              input.current?.click();
            }}
          >
            {busy ? t("import.uploading") : t("import.pickFile")}
          </Button>
        </div>
      )}
      {phase.kind === "mapping" && (
        <MappingStep
          form={phase.form}
          parse={phase.parse}
          leadSources={leadSources.data ?? []}
          customFields={customFields.data ?? []}
          busy={busy}
          onFormChange={(form) => {
            setPhase({ kind: "mapping", form, parse: phase.parse });
          }}
          onBack={() => {
            setPhase({ kind: "upload" });
          }}
          onValidate={async (form) => {
            const result = await runCommit(form, true);
            if (result !== null) {
              setPhase({ kind: "preview", form, parse: phase.parse, result });
            }
          }}
        />
      )}
      {phase.kind === "preview" && (
        <PreviewStep
          result={phase.result}
          busy={busy}
          onBack={() => {
            setPhase({ kind: "mapping", form: phase.form, parse: phase.parse });
          }}
          onCommit={async () => {
            const result = await runCommit(phase.form, false);
            if (result !== null) {
              setPhase({ kind: "done", result });
            }
          }}
        />
      )}
      {phase.kind === "done" && (
        <DoneStep
          result={phase.result}
          onStartOver={() => {
            setPhase({ kind: "upload" });
          }}
        />
      )}
    </section>
  );
}

/** Шаг сопоставления колонок файла с полями клиента. */
function MappingStep({
  form,
  parse,
  leadSources,
  customFields,
  busy,
  onFormChange,
  onBack,
  onValidate,
}: {
  readonly form: ImportForm;
  readonly parse: ClientImportParseResponse;
  readonly leadSources: readonly LeadSourceDetailResponse[];
  readonly customFields: readonly CustomFieldDefinition[];
  readonly busy: boolean;
  readonly onFormChange: (form: ImportForm) => void;
  readonly onBack: () => void;
  readonly onValidate: (form: ImportForm) => Promise<void>;
}) {
  const { t } = useI18n();
  const canValidate = nameColumnCount(form.columnMapping) === 1 && !busy;

  const setTarget = (column: string, target: ImportTarget): void => {
    onFormChange({ ...form, columnMapping: { ...form.columnMapping, [column]: target } });
  };

  const setGenderValue = (value: string, gender: Gender): void => {
    onFormChange({ ...form, genderMapping: { ...form.genderMapping, [value]: gender } });
  };

  const setLeadSourceSkip = (value: string): void => {
    onFormChange({
      ...form,
      leadSourceMapping: { ...form.leadSourceMapping, [value]: { type: "skip" } },
    });
  };

  const setLeadSourceExisting = (value: string, id: string): void => {
    const leadSourceId = leadSources.find((s) => s.id === id)?.id;
    if (leadSourceId === undefined) {
      return;
    }
    onFormChange({
      ...form,
      leadSourceMapping: {
        ...form.leadSourceMapping,
        [value]: { type: "use_existing", id: leadSourceId },
      },
    });
  };

  const setLeadSourceCreate = (value: string): void => {
    onFormChange({
      ...form,
      leadSourceMapping: {
        ...form.leadSourceMapping,
        [value]: { type: "create_new", name: value },
      },
    });
  };

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted-foreground">
        {t("import.rowsCount", { count: parse.totalRows })}
      </p>
      <div className="space-y-4">
        {parse.columns.map((column) => (
          <div key={column} className="space-y-2 rounded-md border p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {t("import.columnTarget", { column })}
              </span>
              <NativeSelect
                value={targetToOptionValue(form.columnMapping[column] ?? { type: "skip" })}
                className="w-auto"
                onChange={(event) => {
                  setTarget(column, optionValueToTarget(event.target.value));
                }}
              >
                <option value="skip">{t("import.target.skip")}</option>
                <option value="name">{t("import.target.name")}</option>
                <option value="birthday">{t("import.target.birthday")}</option>
                <option value="gender">{t("import.target.gender")}</option>
                <option value="lead_source">{t("import.target.lead_source")}</option>
                <option value="balance">{t("import.target.balance")}</option>
                {customFields.map((field) => (
                  <option key={field.fieldKey} value={`custom_field:${field.fieldKey}`}>
                    {t("import.target.custom_field", { label: field.label })}
                  </option>
                ))}
              </NativeSelect>
            </div>
            {form.columnMapping[column]?.type === "gender" && (
              <div className="space-y-1 pl-4">
                <p className="text-xs font-medium text-muted-foreground">
                  {t("import.genderMappingTitle", { column })}
                </p>
                {(parse.uniqueValuesPerColumn[column] ?? []).map((value) => (
                  <div key={value} className="flex items-center gap-2">
                    <span className="min-w-0 flex-1 truncate text-sm">{value}</span>
                    <NativeSelect
                      value={form.genderMapping[value] ?? form.defaultGender}
                      className="w-auto"
                      onChange={(event) => {
                        if (event.target.value === "MALE" || event.target.value === "FEMALE") {
                          setGenderValue(value, event.target.value);
                        }
                      }}
                    >
                      <option value="MALE">{t("gender.MALE")}</option>
                      <option value="FEMALE">{t("gender.FEMALE")}</option>
                    </NativeSelect>
                  </div>
                ))}
              </div>
            )}
            {form.columnMapping[column]?.type === "lead_source" && (
              <div className="space-y-1 pl-4">
                <p className="text-xs font-medium text-muted-foreground">
                  {t("import.leadSourceMappingTitle", { column })}
                </p>
                {(parse.uniqueValuesPerColumn[column] ?? []).map((value) => {
                  const action = form.leadSourceMapping[value] ?? {
                    type: "create_new",
                    name: value,
                  };
                  return (
                    <div key={value} className="flex items-center gap-2">
                      <span className="min-w-0 flex-1 truncate text-sm">{value}</span>
                      <NativeSelect
                        value={action.type === "use_existing" ? action.id : action.type}
                        className="w-auto"
                        onChange={(event) => {
                          const selected = event.target.value;
                          if (selected === "skip") {
                            setLeadSourceSkip(value);
                          } else if (selected === "create_new") {
                            setLeadSourceCreate(value);
                          } else {
                            setLeadSourceExisting(value, selected);
                          }
                        }}
                      >
                        <option value="create_new">
                          {t("import.leadSourceAction.create_new")}
                        </option>
                        <option value="skip">{t("import.leadSourceAction.skip")}</option>
                        {leadSources.map((source) => (
                          <option key={source.id} value={source.id}>
                            {source.name}
                          </option>
                        ))}
                      </NativeSelect>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="import-default-gender">{t("import.defaultGender")}</Label>
          <NativeSelect
            id="import-default-gender"
            value={form.defaultGender}
            onChange={(event) => {
              if (event.target.value === "MALE" || event.target.value === "FEMALE") {
                onFormChange({ ...form, defaultGender: event.target.value });
              }
            }}
          >
            <option value="MALE">{t("gender.MALE")}</option>
            <option value="FEMALE">{t("gender.FEMALE")}</option>
          </NativeSelect>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="import-date-format">{t("import.dateFormat")}</Label>
          <Input
            id="import-date-format"
            value={form.dateFormat}
            placeholder="dd.MM.yyyy"
            onChange={(event) => {
              onFormChange({ ...form, dateFormat: event.target.value });
            }}
          />
          <p className="text-xs text-muted-foreground">{t("import.dateFormatHint")}</p>
        </div>
      </div>
      <div className="flex gap-2">
        <Button variant="outline" onClick={onBack} disabled={busy}>
          {t("import.back")}
        </Button>
        <Button
          disabled={!canValidate}
          aria-busy={busy}
          onClick={() => {
            void onValidate(form);
          }}
        >
          {busy ? t("import.validating") : t("import.validate")}
        </Button>
      </div>
    </div>
  );
}

/** Шаг проверки без записи: сводка и построчный результат. */
function PreviewStep({
  result,
  busy,
  onBack,
  onCommit,
}: {
  readonly result: ClientImportCommitResponse;
  readonly busy: boolean;
  readonly onBack: () => void;
  readonly onCommit: () => Promise<void>;
}) {
  const { t } = useI18n();
  const errorRows = result.rows.filter((row) => row.status === "ERROR");
  return (
    <div className="space-y-4">
      <h2 className="text-base font-semibold">{t("import.previewTitle")}</h2>
      <p>{t("import.totalRows", { total: result.totalRows })}</p>
      <p>{t("import.importedRows", { imported: result.imported })}</p>
      <p>{t("import.skippedRows", { skipped: result.skipped })}</p>
      {errorRows.length > 0 && (
        <ul className="max-h-64 space-y-1 overflow-y-auto rounded-md border p-3 text-sm">
          {errorRows.map((row) => (
            <li key={row.rowNumber} className="text-destructive">
              {t("import.rowError", { row: row.rowNumber, errors: row.errors.join("; ") })}
            </li>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <Button variant="outline" onClick={onBack} disabled={busy}>
          {t("import.back")}
        </Button>
        <Button
          disabled={busy || result.imported === 0}
          aria-busy={busy}
          onClick={() => {
            void onCommit();
          }}
        >
          {busy ? t("import.importing") : t("import.commit")}
        </Button>
      </div>
    </div>
  );
}

/** Шаг завершения: итоги импорта. */
function DoneStep({
  result,
  onStartOver,
}: {
  readonly result: ClientImportCommitResponse;
  readonly onStartOver: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="space-y-3">
      <h2 className="text-base font-semibold">{t("import.doneTitle")}</h2>
      <p>{t("import.doneImported", { imported: result.imported })}</p>
      <p>{t("import.doneSkipped", { skipped: result.skipped })}</p>
      {result.createdLeadSources.length > 0 && (
        <p>
          {t("import.createdLeadSources", {
            names: result.createdLeadSources.map((s) => s.name).join(", "),
          })}
        </p>
      )}
      <Button onClick={onStartOver}>{t("import.startOver")}</Button>
    </div>
  );
}
