package org.athletica.crm.demo

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.athletica.crm.core.Gender
import kotlin.random.Random

/** Вымышленный человек наполнения: полное имя [name] и пол [gender]. */
internal data class DemoPerson(
    /** Фамилия и имя. */
    val name: String,
    /** Пол, согласованный с именем и фамилией. */
    val gender: Gender,
    /** Фамилия латиницей для адреса почты. */
    val latinSurname: String,
)

/** Фамилии в мужской форме с транслитерацией для адресов почты. */
private val SURNAMES =
    listOf(
        "Иванов" to "ivanov", "Смирнов" to "smirnov", "Кузнецов" to "kuznetsov", "Попов" to "popov",
        "Васильев" to "vasiliev", "Петров" to "petrov", "Соколов" to "sokolov", "Михайлов" to "mikhailov",
        "Новиков" to "novikov", "Фёдоров" to "fedorov", "Морозов" to "morozov", "Волков" to "volkov",
        "Алексеев" to "alekseev", "Лебедев" to "lebedev", "Семёнов" to "semenov", "Егоров" to "egorov",
        "Павлов" to "pavlov", "Козлов" to "kozlov", "Степанов" to "stepanov", "Николаев" to "nikolaev",
        "Орлов" to "orlov", "Андреев" to "andreev", "Макаров" to "makarov", "Никитин" to "nikitin",
        "Захаров" to "zakharov", "Зайцев" to "zaitsev", "Соловьёв" to "soloviev", "Борисов" to "borisov",
        "Яковлев" to "yakovlev", "Григорьев" to "grigoriev", "Романов" to "romanov", "Воробьёв" to "vorobiev",
        "Сергеев" to "sergeev", "Кузьмин" to "kuzmin", "Фролов" to "frolov", "Александров" to "aleksandrov",
        "Дмитриев" to "dmitriev", "Королёв" to "korolev", "Гусев" to "gusev", "Киселёв" to "kiselev",
        "Ильин" to "ilin", "Максимов" to "maksimov", "Поляков" to "polyakov", "Сорокин" to "sorokin",
        "Виноградов" to "vinogradov", "Ковалёв" to "kovalev", "Белов" to "belov", "Медведев" to "medvedev",
        "Антонов" to "antonov", "Тарасов" to "tarasov", "Жуков" to "zhukov", "Баранов" to "baranov",
        "Филиппов" to "filippov", "Комаров" to "komarov", "Давыдов" to "davydov", "Беляев" to "belyaev",
        "Герасимов" to "gerasimov", "Богданов" to "bogdanov", "Осипов" to "osipov", "Сидоров" to "sidorov",
    )

/** Мужские имена. */
private val MALE_NAMES =
    listOf(
        "Александр", "Дмитрий", "Максим", "Сергей", "Андрей", "Алексей", "Артём", "Илья", "Кирилл", "Михаил",
        "Никита", "Матвей", "Роман", "Егор", "Арсений", "Иван", "Денис", "Евгений", "Тимофей", "Владимир",
        "Павел", "Глеб", "Марк", "Лев", "Фёдор", "Даниил", "Степан", "Николай", "Ярослав", "Григорий",
    )

/** Женские имена. */
private val FEMALE_NAMES =
    listOf(
        "Анастасия", "Мария", "Анна", "Виктория", "Екатерина", "Наталья", "Марина", "Полина", "Дарья", "Алиса",
        "Ксения", "Елизавета", "Софья", "Варвара", "Вероника", "Ольга", "Татьяна", "Юлия", "Ирина", "Алина",
        "Ева", "Милана", "Арина", "Валерия", "Кристина", "Светлана", "Елена", "Василиса", "Ульяна", "Таисия",
    )

/** Заметки о клиенте, как их пишут администраторы. */
private val NOTES =
    listOf(
        "Просили звонить после 18:00",
        "Есть аллергия на цитрусовые, учитывать на сборах",
        "Приводит младшего брата, интересуются второй группой",
        "Хочет перейти на утренние занятия",
        "Пришли по рекомендации знакомых",
        "Нужна справка для школы о посещении",
        "Предпочитает оплату картой",
        "После травмы колена, нагрузку согласовать с тренером",
        "Интересуется индивидуальными тренировками",
        "Родители просят присылать фото с соревнований",
    )

/** Случайный вымышленный человек: имя и фамилия согласованы с полом. */
internal fun Random.person(): DemoPerson {
    val gender = if (nextBoolean()) Gender.MALE else Gender.FEMALE
    val (surname, latin) = SURNAMES.random(this)
    return when (gender) {
        Gender.MALE -> DemoPerson("$surname ${MALE_NAMES.random(this)}", gender, latin)
        Gender.FEMALE -> DemoPerson("${surname}а ${FEMALE_NAMES.random(this)}", gender, "${latin}a")
    }
}

/** Дата рождения человека возрастом от [minAge] до [maxAge] лет на дату [today]. */
internal fun Random.birthday(today: LocalDate, minAge: Int, maxAge: Int): LocalDate = today.minus(DatePeriod(days = nextInt(minAge * 365, maxAge * 365)))

/** Телефон с несуществующим кодом `+7 000`, чтобы ни один номер не указывал на реального человека. */
internal fun Random.phone(): String = "+7 000 ${digits(3)}-${digits(2)}-${digits(2)}"

/** Строка из [count] случайных цифр. */
private fun Random.digits(count: Int): String = (1..count).joinToString("") { nextInt(10).toString() }

/** Адрес почты человека [person] в зарезервированном домене `example.com`. */
internal fun Random.email(person: DemoPerson): String = "${person.latinSurname}${nextInt(10, 1000)}@example.com"

/** Случайная заметка о клиенте. */
internal fun Random.note(): String = NOTES.random(this)
