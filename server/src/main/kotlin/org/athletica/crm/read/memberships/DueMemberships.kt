package org.athletica.crm.read.memberships

import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid
import kotlin.time.Instant

/** Абонементы всех организаций, которым по местной дате организации положено действие. */
context(tr: Transaction)
internal suspend fun dueMemberships(now: Instant): List<Pair<OrgId, MembershipId>> =
    tr
        .sql(
            """
            WITH local AS (
                SELECT m.*, (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today
                FROM memberships m
                JOIN organizations o ON o.id = m.org_id
                WHERE m.state IN ('INACTIVE', 'ACTIVE')
            )
            SELECT org_id, id FROM local
            WHERE (state = 'INACTIVE' AND activation_mode = 'ON_DATE' AND activate_on <= today)
               OR (state = 'INACTIVE' AND activation_mode = 'ON_FIRST_VISIT' AND activation_deadline <= today)
               OR (state = 'ACTIVE' AND freeze_end < today)
               OR (state = 'ACTIVE' AND close_mode = 'ON_EXPIRY' AND end_date < today)
               OR (state = 'ACTIVE' AND close_notified_at IS NULL
                   AND EXISTS (SELECT 1 FROM employees e WHERE e.id = issued_by AND e.is_active)
                   AND ((close_mode = 'MANUAL' AND (end_date < today OR sessions_remaining = 0))
                     OR (close_mode = 'ON_LAST_SESSION' AND end_date < today AND sessions_remaining > 0)))
            ORDER BY org_id, id
            """.trimIndent(),
        )
        .bind("now", now)
        .list { it.asUuid("org_id").toOrgId() to it.asUuid("id").toMembershipId() }
