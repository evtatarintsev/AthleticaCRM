/**
 * Русский словарь кабинета клиента — источник ключей и параметров сообщений.
 * Остальные словари объявляются как `Messages<typeof ru>`.
 */
export const ru = {
  "app.title": "Кабинет клиента",
  "app.loading": "Загрузка…",
  "app.retry": "Повторить",
  "error.unavailable": "Сервер недоступен. Проверьте подключение и попробуйте ещё раз",
  "error.contract": "Что-то пошло не так. Обновите страницу",
  "signIn.phoneTitle": "Вход в кабинет",
  "signIn.phoneHint": "Введите номер телефона, который вы сообщили клубу",
  "signIn.phoneLabel": "Номер телефона",
  "signIn.getCode": "Получить код",
  "signIn.codeTitle": "Введите код",
  "signIn.codeSent": "Отправили код на номер {phone}",
  "signIn.codeLabel": "Код из сообщения",
  "signIn.login": "Войти",
  "signIn.retryIn": "Новый код можно запросить через {time}",
  "signIn.resend": "Отправить ещё раз",
  "signIn.viaSms": "Отправить по SMS",
  "signIn.changePhone": "Изменить номер",
  "signIn.termsTitle": "Условия использования",
  "signIn.termsText":
    "Чтобы войти, примите условия использования кабинета и политику обработки персональных данных.",
  "signIn.termsAccept": "Принимаю условия и вхожу",
  "invitations.title": "Вас пригласили",
  "invitations.hint":
    "Ваш номер указан у клиентов этих организаций. Подключитесь — и увидите их данные. Не узнаёте организацию? Нажмите «Это не я», и мы сообщим администратору.",
  "invitations.invitedAt": "Приглашение от {date}",
  "invitations.clients": {
    one: "{count} клиент с вашим номером",
    few: "{count} клиента с вашим номером",
    other: "{count} клиентов с вашим номером",
  },
  "invitations.connect": "Подключить",
  "invitations.notMe": "Это не я",
  "invitations.back": "Назад",
  "profiles.empty": "Профилей пока нет — спросите у администратора",
  "profiles.switch": "Сменить профиль",
  "profiles.title": "Чьи данные показать",
  "profiles.newInvitations": "Новые приглашения",
  "profiles.isolation": "Каждая организация видит только своих клиентов.",
  "home.greeting": "Здравствуйте!",
  "home.cabinetOf": "Это кабинет клиента {name}",
} as const;
