/**
 * Переход в основное приложение после входа.
 * Сессия уже лежит в HttpOnly-cookie, поэтому старый клиент на `/` подхватит её сам.
 */
export function redirectToApp(): void {
  window.location.assign("/");
}
