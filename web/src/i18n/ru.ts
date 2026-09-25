/**
 * Русский словарь — источник ключей и параметров сообщений.
 * Остальные словари объявляются как `Messages<typeof ru>`.
 */
export const ru = {
  "app.name": "AthleticaCRM",

  "auth.loginTitle": "Вход",
  "auth.loginSubtitle": "Войдите, чтобы продолжить работу",
  "auth.registerTitle": "Регистрация",
  "auth.registerSubtitle": "Создайте организацию — это займёт минуту",
  "auth.branchTitle": "Выберите филиал",
  "auth.branchSubtitle": "Ваш аккаунт открыт в нескольких филиалах",
  "auth.email": "Email",
  "auth.password": "Пароль",
  "auth.orgName": "Название организации",
  "auth.yourName": "Ваше имя",
  "auth.timezone": "Часовой пояс",
  "auth.currency": "Валюта",
  "auth.currencyHint": "Изменить валюту после регистрации нельзя",
  "auth.passwordShow": "Показать пароль",
  "auth.passwordHide": "Скрыть пароль",
  "auth.actionLogin": "Войти",
  "auth.actionRegister": "Зарегистрироваться",
  "auth.noAccount": "Нет аккаунта?",
  "auth.haveAccount": "Уже есть аккаунт?",

  "action.back": "Назад",
  "action.close": "Закрыть",
  "action.retry": "Повторить",

  "error.invalidCredentials": "Неверный логин или пароль",
  "error.noBranches": "У аккаунта нет доступа ни к одному филиалу",
  "error.serviceUnavailable": "Сервис недоступен. Проверьте соединение",
  "error.registrationFailed": "Ошибка регистрации. Попробуйте ещё раз",
  "error.required": "Заполните поле",
  "error.invalidEmail": "Введите email",

  "nav.label": "Разделы",
  "nav.openMenu": "Открыть меню",
  "nav.home": "Главная",
  "nav.schedule": "Расписание",
  "nav.clients": "Клиенты",
  "nav.groups": "Группы",
  "nav.employees": "Сотрудники",
  "nav.tasks": "Задачи",
  "nav.settings": "Настройки",

  "account.menu": "Меню аккаунта",
  "account.branch": "Филиал «{name}»",
  "account.profile": "Профиль",
  "account.switchBranch": "Сменить филиал",
  "account.language": "Язык",
  "account.logout": "Выйти",

  "language.ru": "Русский",
  "language.en": "English",

  "home.title": "Главная",
  "home.notMigrated":
    "Разделы переносятся в новый интерфейс постепенно. Пока они открываются в основном приложении.",
  "home.openApp": "Открыть основное приложение",

  "notFound.title": "Страница не найдена",
  "notFound.text": "Проверьте адрес или вернитесь на главную.",
  "notFound.home": "На главную",

  "session.loadError": "Не удалось загрузить данные. Проверьте соединение и повторите.",
} as const;
