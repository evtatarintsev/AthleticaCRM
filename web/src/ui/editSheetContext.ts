import { createContext, useContext } from "react";

/** Состояние формы, от которого зависит закрытие панели. */
export interface EditSheetGuard {
  /** Есть несохранённые изменения. */
  readonly dirty: boolean;
  /** Идёт сохранение. */
  readonly submitting: boolean;
}

/** Управление панелью, доступное её содержимому и вложенным панелям. */
export interface EditSheetControl {
  /** Закрывает панель без вопросов — например, после успешного сохранения. */
  readonly close: () => void;
  /** Просит закрыть панель; при несохранённых изменениях сначала спрашивает подтверждение. */
  readonly requestClose: () => void;
  /** Сообщает панели состояние формы [guard]. */
  readonly reportGuard: (guard: EditSheetGuard) => void;
  /** Сообщает панели, открыта ли поверх неё вложенная панель. */
  readonly reportChild: (open: boolean) => void;
}

/** Ближайшая открытая панель [EditSheet]; `null` вне панели. */
export const EditSheetContext = createContext<EditSheetControl | null>(null);

/** Управление ближайшей панелью; вызывается только внутри [EditSheet]. */
export function useEditSheet(): EditSheetControl {
  const control = useContext(EditSheetContext);
  if (control === null) {
    throw new Error("useEditSheet вызван вне EditSheet");
  }
  return control;
}
