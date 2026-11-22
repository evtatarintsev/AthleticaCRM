import { BASE_PATH } from "@/config";

/** Адрес публичной страницы виджета с ключом [publicKey] на хосте [origin] веб-клиента. */
export function widgetUrl(origin: string, publicKey: string): string {
  return `${origin}${BASE_PATH}/widget/${encodeURIComponent(publicKey)}`;
}

/** Код iframe для сайта клуба: страница виджета [url] с подписью [title] для экранных дикторов. */
export function widgetIframeCode(url: string, title: string): string {
  return `<iframe src="${url}" title="${title}" width="100%" height="720" style="border:0" loading="lazy"></iframe>`;
}
