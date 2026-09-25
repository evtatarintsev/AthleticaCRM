/**
 * Строки интерфейса. Пока только русский; при добавлении языков этот объект
 * станет одним из словарей с тем же набором ключей.
 */
export const t = {
  appName: "AthleticaCRM",
  loginTitle: "Вход",
  loginSubtitle: "Войдите, чтобы продолжить работу",
  registerTitle: "Регистрация",
  registerSubtitle: "Создайте организацию — это займёт минуту",
  branchTitle: "Выберите филиал",
  branchSubtitle: "Ваш аккаунт открыт в нескольких филиалах",
  email: "Email",
  password: "Пароль",
  orgName: "Название организации",
  yourName: "Ваше имя",
  timezone: "Часовой пояс",
  currency: "Валюта",
  currencyHint: "Изменить валюту после регистрации нельзя",
  passwordShow: "Показать пароль",
  passwordHide: "Скрыть пароль",
  actionLogin: "Войти",
  actionRegister: "Зарегистрироваться",
  actionBack: "Назад",
  noAccount: "Нет аккаунта?",
  haveAccount: "Уже есть аккаунт?",
  errorInvalidCredentials: "Неверный логин или пароль",
  errorNoBranches: "У аккаунта нет доступа ни к одному филиалу",
  errorServiceUnavailable: "Сервис недоступен. Проверьте соединение",
  errorRegistrationFailed: "Ошибка регистрации. Попробуйте ещё раз",
} as const;
