package org.athletica.crm.core.sportcatalog

/**
 * Каталог видов спорта и дисциплин, общий для всех организаций.
 * Подсказывает название при добавлении дисциплины в организацию; дисциплина организации
 * ссылку на элемент каталога не хранит.
 */
object SportCatalog {
    /** Виды спорта в порядке показа. */
    val sports: List<CatalogSport> =
        listOf(
            sport("aquatics", "Плавание", "Swimming") {
                discipline("swimming", "Плавание", "Swimming")
                discipline("learn_to_swim", "Обучение плаванию", "Learn to swim", "начальное плавание")
                discipline("artistic_swimming", "Синхронное плавание", "Artistic swimming", "синхронка", "synchronized swimming")
                discipline("diving", "Прыжки в воду", "Diving")
                discipline("water_polo", "Водное поло", "Water polo")
            },
            sport("gymnastics", "Гимнастика", "Gymnastics") {
                discipline("artistic_gymnastics", "Спортивная гимнастика", "Artistic gymnastics")
                discipline("rhythmic_gymnastics", "Художественная гимнастика", "Rhythmic gymnastics", "художка")
                discipline("acrobatic_gymnastics", "Спортивная акробатика", "Acrobatic gymnastics", "акробатика")
                discipline("trampoline", "Прыжки на батуте", "Trampoline", "батут")
                discipline("aesthetic_gymnastics", "Эстетическая гимнастика", "Aesthetic group gymnastics")
                discipline("cheerleading", "Чир спорт", "Cheerleading", "чирлидинг", "cheer")
            },
            sport("athletics", "Лёгкая атлетика", "Athletics") {
                discipline("track_and_field", "Лёгкая атлетика", "Track and field", "athletics")
                discipline("running", "Бег", "Running", "jogging")
                discipline("trail_running", "Трейлраннинг", "Trail running", "трейл")
                discipline("race_walking", "Спортивная ходьба", "Race walking")
                discipline("nordic_walking", "Скандинавская ходьба", "Nordic walking")
            },
            sport("team_sports", "Командные виды", "Team sports") {
                discipline("football", "Футбол", "Football", "soccer")
                discipline("futsal", "Мини-футбол", "Futsal", "футзал")
                discipline("basketball", "Баскетбол", "Basketball")
                discipline("volleyball", "Волейбол", "Volleyball")
                discipline("beach_volleyball", "Пляжный волейбол", "Beach volleyball")
                discipline("ice_hockey", "Хоккей", "Ice hockey")
                discipline("handball", "Гандбол", "Handball")
                discipline("rugby", "Регби", "Rugby")
                discipline("floorball", "Флорбол", "Floorball")
            },
            sport("racket_sports", "Ракеточные", "Racket sports") {
                discipline("tennis", "Теннис", "Tennis", "большой теннис")
                discipline("table_tennis", "Настольный теннис", "Table tennis", "пинг-понг", "ping pong")
                discipline("badminton", "Бадминтон", "Badminton")
                discipline("padel", "Падел", "Padel", "padel-теннис")
                discipline("squash", "Сквош", "Squash")
            },
            sport("wrestling", "Борьба", "Wrestling & grappling") {
                discipline("freestyle_wrestling", "Вольная борьба", "Freestyle wrestling")
                discipline("greco_roman_wrestling", "Греко-римская борьба", "Greco-Roman wrestling", "классическая борьба")
                discipline("judo", "Дзюдо", "Judo")
                discipline("sambo", "Самбо", "Sambo")
                discipline("bjj", "Бразильское джиу-джитсу", "Brazilian jiu-jitsu", "BJJ", "БЖЖ")
                discipline("grappling", "Грэпплинг", "Grappling")
            },
            sport("striking", "Ударные единоборства", "Striking") {
                discipline("boxing", "Бокс", "Boxing")
                discipline("kickboxing", "Кикбоксинг", "Kickboxing")
                discipline("muay_thai", "Тайский бокс", "Muay Thai", "муай тай")
                discipline("karate", "Карате", "Karate", "каратэ")
                discipline("taekwondo", "Тхэквондо", "Taekwondo", "тэквондо")
                discipline("mma", "MMA", "MMA", "ММА", "смешанные единоборства")
                discipline("wushu", "Ушу", "Wushu", "кунг-фу")
            },
            sport("fencing", "Фехтование", "Fencing") {
                discipline("epee", "Шпага", "Épée", "epee")
                discipline("foil", "Рапира", "Foil")
                discipline("sabre", "Сабля", "Sabre", "saber")
                discipline("historical_fencing", "Историческое фехтование", "Historical fencing", "HEMA")
            },
            sport("dance", "Танцы", "Dance") {
                discipline("hip_hop", "Хип-хоп", "Hip-hop", "hip hop")
                discipline("breaking", "Брейкинг", "Breaking", "брейк-данс", "break dance", "b-boying")
                discipline("jazz_funk", "Jazz-funk", "Jazz-funk", "джаз-фанк")
                discipline("contemporary", "Contemporary", "Contemporary", "контемп", "контемпорари")
                discipline("high_heels", "High heels", "High heels", "хилс", "heels")
                discipline("k_pop", "K-pop", "K-pop", "кей-поп", "k-pop cover")
                discipline("vogue", "Vogue", "Vogue", "вог")
                discipline("ballroom", "Бальные танцы", "Ballroom dance", "спортивные бальные")
                discipline("latin", "Латина", "Latin dance", "ча-ча-ча", "румба", "джайв")
                discipline("samba", "Самба", "Samba", "samba")
                discipline("ballet", "Классическая хореография", "Ballet", "балет", "хореография")
                discipline("folk_dance", "Народные танцы", "Folk dance")
                discipline("pole_dance", "Pole dance", "Pole dance", "пилон", "пол дэнс")
            },
            sport("fitness", "Фитнес", "Fitness") {
                discipline("gpp", "ОФП", "General physical training", "общая физическая подготовка", "GPP")
                discipline("functional_training", "Функциональный тренинг", "Functional training", "функционалка")
                discipline("crossfit", "Кроссфит", "CrossFit")
                discipline("yoga", "Йога", "Yoga")
                discipline("pilates", "Пилатес", "Pilates")
                discipline("stretching", "Стретчинг", "Stretching", "растяжка")
                discipline("aerobics", "Аэробика", "Aerobics", "степ", "step")
                discipline("aqua_aerobics", "Аквааэробика", "Aqua aerobics", "аквафитнес")
                discipline("zumba", "Зумба", "Zumba")
                discipline("gym", "Тренажёрный зал", "Gym", "тренажёрка", "качалка")
            },
            sport("winter_sports", "Зимние виды", "Winter sports") {
                discipline("figure_skating", "Фигурное катание", "Figure skating", "фигурка")
                discipline("speed_skating", "Конькобежный спорт", "Speed skating")
                discipline("cross_country_skiing", "Лыжные гонки", "Cross-country skiing", "беговые лыжи")
                discipline("alpine_skiing", "Горные лыжи", "Alpine skiing")
                discipline("snowboarding", "Сноуборд", "Snowboarding")
                discipline("biathlon", "Биатлон", "Biathlon")
            },
            sport("cycling", "Велоспорт", "Cycling") {
                discipline("road_cycling", "Шоссейный велоспорт", "Road cycling", "шоссе")
                discipline("mountain_biking", "Маунтинбайк", "Mountain biking", "MTB", "МТБ")
                discipline("bmx", "BMX", "BMX", "бмх")
                discipline("track_cycling", "Трек", "Track cycling")
            },
            sport("action_sports", "Экстрим", "Action sports") {
                discipline("skateboarding", "Скейтбординг", "Skateboarding", "скейт")
                discipline("scootering", "Трюковой самокат", "Scootering", "самокат", "scooter")
                discipline("inline_skating", "Роллеры", "Inline skating", "ролики")
                discipline("parkour", "Паркур", "Parkour", "фриран", "freerun")
            },
            sport("climbing", "Скалолазание", "Climbing") {
                discipline("sport_climbing", "Скалолазание", "Sport climbing", "climbing")
                discipline("bouldering", "Боулдеринг", "Bouldering")
            },
            sport("water_sports", "Водные виды", "Water sports") {
                discipline("rowing", "Академическая гребля", "Rowing")
                discipline("canoe_kayak", "Гребля на байдарках и каноэ", "Canoe & kayak", "байдарка")
                discipline("sailing", "Парусный спорт", "Sailing", "яхтинг")
                discipline("sup", "SUP", "Stand-up paddle", "сап", "сапсёрфинг")
                discipline("surfing", "Серфинг", "Surfing", "серф")
            },
            sport("equestrian", "Конный спорт", "Equestrian") {
                discipline("horse_riding", "Верховая езда", "Horse riding")
                discipline("show_jumping", "Конкур", "Show jumping")
                discipline("dressage", "Выездка", "Dressage")
            },
            sport("shooting", "Стрельба", "Shooting") {
                discipline("archery", "Стрельба из лука", "Archery", "лук")
                discipline("marksmanship", "Пулевая стрельба", "Shooting")
            },
            sport("mind_sports", "Интеллектуальные", "Mind sports") {
                discipline("chess", "Шахматы", "Chess")
                discipline("draughts", "Шашки", "Draughts", "checkers")
                discipline("go", "Го", "Go")
            },
        )
}

/** Сборщик дисциплин одного вида спорта. */
private class DisciplinesBuilder {
    /** Добавленные дисциплины в порядке добавления. */
    val disciplines = mutableListOf<CatalogDiscipline>()

    /** Добавляет дисциплину с ключом [key], названиями [ru] и [en] и синонимами [aliases]. */
    fun discipline(
        key: String,
        ru: String,
        en: String,
        vararg aliases: String,
    ) {
        disciplines += CatalogDiscipline(key, CatalogNames(ru = ru, en = en), aliases.toList())
    }
}

/** Вид спорта с ключом [key], названиями [ru] и [en] и дисциплинами из [block]. */
private fun sport(
    key: String,
    ru: String,
    en: String,
    block: DisciplinesBuilder.() -> Unit,
): CatalogSport = CatalogSport(key, CatalogNames(ru = ru, en = en), DisciplinesBuilder().apply(block).disciplines.toList())
