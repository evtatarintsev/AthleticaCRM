/**
 * Текстовое значение поля [name] из [form].
 * Файловые поля и отсутствующие значения дают пустую строку.
 */
export function formText(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}
