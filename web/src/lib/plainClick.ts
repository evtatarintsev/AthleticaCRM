import type { MouseEvent } from "react";

/**
 * Истина, если [event] — обычный клик левой кнопкой без модификаторов: его перехватывает
 * панель. Клики с Ctrl/Cmd/Shift/Alt и другими кнопками остаются переходом по ссылке.
 */
export function isPlainClick(event: MouseEvent): boolean {
  return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
}
