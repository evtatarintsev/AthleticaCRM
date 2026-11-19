/** Подменяет `window.matchMedia` (в jsdom его нет): любой медиа-запрос совпадает, если [matches]. */
export function stubMatchMedia(matches: boolean): void {
  window.matchMedia = (media: string): MediaQueryList => ({
    matches,
    media,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  });
}
