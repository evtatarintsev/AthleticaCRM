import {
  CustomFieldKeySchema,
  type ClientImportCommitRequest,
  type CustomFieldKey,
  type Gender,
  type ImportTarget,
  type LeadSourceAction,
  type UploadId,
} from "@/api/generated/contracts";

/** Состояние формы импорта: сопоставление колонок и значений, заполняется на шаге «Сопоставление». */
export interface ImportForm {
  readonly uploadId: UploadId;
  readonly originalName: string;
  /** Цель для каждой колонки файла; изначально все — `skip`. */
  readonly columnMapping: Readonly<Record<string, ImportTarget>>;
  readonly defaultGender: Gender;
  /** Соответствие значения колонки полу — только для колонок с целью `gender`. */
  readonly genderMapping: Readonly<Record<string, Gender>>;
  /** Соответствие значения колонки источнику — только для колонок с целью `lead_source`. */
  readonly leadSourceMapping: Readonly<Record<string, LeadSourceAction>>;
  /** Формат даты (шаблон `DateTimeFormatter`); пустая строка — сервер определит сам. */
  readonly dateFormat: string;
}

/** Префикс значения select для цели «дополнительное поле». */
const CUSTOM_FIELD_PREFIX = "custom_field:";

/** Значение `<select>` для цели [target]: простые цели — свой код, доп. поле — с префиксом и ключом. */
export function targetToOptionValue(target: ImportTarget): string {
  return target.type === "custom_field" ? `${CUSTOM_FIELD_PREFIX}${target.key}` : target.type;
}

/** Цель импорта из значения select [value]. */
export function optionValueToTarget(value: string): ImportTarget {
  if (value.startsWith(CUSTOM_FIELD_PREFIX)) {
    const key = CustomFieldKeySchema.safeParse(value.slice(CUSTOM_FIELD_PREFIX.length));
    if (key.success) {
      return { type: "custom_field", key: key.data };
    }
    return { type: "skip" };
  }
  switch (value) {
    case "name":
      return { type: "name" };
    case "birthday":
      return { type: "birthday" };
    case "gender":
      return { type: "gender" };
    case "lead_source":
      return { type: "lead_source" };
    case "balance":
      return { type: "balance" };
    default:
      return { type: "skip" };
  }
}

/** Сколько колонок сопоставлено с именем клиента — цель обязана встретиться ровно один раз. */
export function nameColumnCount(mapping: Readonly<Record<string, ImportTarget>>): number {
  return Object.values(mapping).filter((target) => target.type === "name").length;
}

/** Колонки, сопоставленные с целью [type], в порядке файла [columns]. */
export function columnsWithTarget(
  columns: readonly string[],
  mapping: Readonly<Record<string, ImportTarget>>,
  type: ImportTarget["type"],
): readonly string[] {
  return columns.filter((column) => mapping[column]?.type === type);
}

/** Ключ доп. поля, выбранного как цель для доп. поля [key], если такая колонка есть. */
export function customFieldKeyOf(target: ImportTarget): CustomFieldKey | null {
  return target.type === "custom_field" ? target.key : null;
}

/** Запрос `/clients/import/commit` из формы [form]; [dryRun] — проверка без записи. */
export function commitRequest(form: ImportForm, dryRun: boolean): ClientImportCommitRequest {
  return {
    uploadId: form.uploadId,
    columnMapping: Object.entries(form.columnMapping).map(([sourceColumn, target]) => ({
      sourceColumn,
      target,
    })),
    defaultGender: form.defaultGender,
    genderMapping: form.genderMapping,
    leadSourceMapping: form.leadSourceMapping,
    dateFormat: form.dateFormat.trim() === "" ? null : form.dateFormat.trim(),
    dryRun,
  };
}
