package org.athletica.crm.storage.sql

/**
 * Правило подбора абонемента для списания занятия — условие и порядок над таблицей
 * `memberships` с псевдонимом `m`. Одно место для списания (`DbMemberships.debitCandidate`)
 * и превью (`DbSessionDebitPreviewView`); пакет нейтральный, не зависит ни от `domain/`, ни от `read/`.
 *
 * Ступени (спека «Подбор абонемента для списания»):
 * 1. активный, дата занятия в сроке, есть занятия или безлимит;
 * 2. неактивированный «с даты», дата активации наступила и не позже даты занятия, дата занятия
 *    в сроке, считая от даты активации;
 * 3. неактивированный «при первом посещении», дата занятия не раньше выдачи и не позже крайней даты.
 *
 * Внутри ступени — по более ранней дате окончания, затем выданный раньше.
 * Скоуп по организации — параметр `:orgId`, его привязывает вызывающий.
 */
object MembershipCandidateSql {
    /**
     * Дата окончания абонемента «с даты», если он активируется датой `m.activate_on`:
     * то же правило включительного срока, что и в домене (`MembershipDuration.endDateFrom`).
     */
    private const val ON_DATE_END =
        """
        (CASE
             WHEN m.duration_unit = 'DAYS' THEN m.activate_on + (m.duration_value - 1)
             WHEN EXTRACT(DAY FROM m.activate_on + make_interval(months => m.duration_value))
                  < EXTRACT(DAY FROM m.activate_on)
                 THEN (m.activate_on + make_interval(months => m.duration_value))::date
             ELSE (m.activate_on + make_interval(months => m.duration_value))::date - 1
         END)
        """

    /**
     * Условие «абонемент подходит для списания» для клиента [clientId] за занятие группы [groupId]
     * от даты [sessionDate] при сегодняшней дате организации [today]. Аргументы — SQL-выражения:
     * параметры (`:clientId`) или колонки внешнего запроса (`r.client_id`).
     */
    fun condition(
        clientId: String,
        groupId: String,
        sessionDate: String,
        today: String,
    ): String =
        """
        m.org_id = :orgId
        AND m.client_id = $clientId
        AND EXISTS (SELECT 1 FROM membership_groups mg WHERE mg.membership_id = m.id AND mg.group_id = $groupId)
        AND (m.sessions_remaining IS NULL OR m.sessions_remaining > 0)
        AND (
            (m.state = 'ACTIVE' AND $sessionDate BETWEEN m.start_date AND m.end_date)
            OR (m.state = 'INACTIVE' AND m.activation_mode = 'ON_DATE'
                AND m.activate_on <= $today
                AND $sessionDate BETWEEN m.activate_on AND $ON_DATE_END)
            OR (m.state = 'INACTIVE' AND m.activation_mode = 'ON_FIRST_VISIT'
                AND $sessionDate >= m.issued_on
                AND (m.activation_deadline IS NULL OR $sessionDate <= m.activation_deadline))
        )
        """

    /** Порядок подбора: ступень, затем более ранняя дата окончания, затем выданный раньше. */
    const val ORDER =
        """
        CASE WHEN m.state = 'ACTIVE' THEN 1 WHEN m.activation_mode = 'ON_DATE' THEN 2 ELSE 3 END,
        CASE WHEN m.state = 'ACTIVE' THEN m.end_date WHEN m.activation_mode = 'ON_DATE' THEN $ON_DATE_END END NULLS LAST,
        m.created_at,
        m.id
        """
}
