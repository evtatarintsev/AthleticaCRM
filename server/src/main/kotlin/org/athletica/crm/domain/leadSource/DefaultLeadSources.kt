package org.athletica.crm.domain.leadSource

import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.i18n.LocalizationKey
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Стартовый набор источников привлечения, создаваемый каждой новой организации.
 * Названия и пояснения записываются один раз на языке регистрации и дальше не переводятся;
 * организация правит справочник сама: предустановленные источники ничем не отличаются
 * от созданных вручную.
 */
object DefaultLeadSources {
    /** Название и пояснение предустановленных источников. */
    private val defaults: List<Pair<LocalizationKey, LocalizationKey>> =
        listOf(
            Messages.DefaultLeadSourceReferral to Messages.DefaultLeadSourceReferralDescription,
            Messages.DefaultLeadSourceSocialMedia to Messages.DefaultLeadSourceSocialMediaDescription,
            Messages.DefaultLeadSourcePaidSocialAds to Messages.DefaultLeadSourcePaidSocialAdsDescription,
            Messages.DefaultLeadSourceWebSearch to Messages.DefaultLeadSourceWebSearchDescription,
            Messages.DefaultLeadSourceMapsAndReviews to Messages.DefaultLeadSourceMapsAndReviewsDescription,
            Messages.DefaultLeadSourceWebsite to Messages.DefaultLeadSourceWebsiteDescription,
            Messages.DefaultLeadSourceWalkIn to Messages.DefaultLeadSourceWalkInDescription,
            Messages.DefaultLeadSourceEvent to Messages.DefaultLeadSourceEventDescription,
            Messages.DefaultLeadSourceSchool to Messages.DefaultLeadSourceSchoolDescription,
            Messages.DefaultLeadSourceOther to Messages.DefaultLeadSourceOtherDescription,
        )

    /** Создаёт предустановленные источники в организации [orgId] с текстами на языке [lang]. */
    context(tr: Transaction)
    suspend fun createFor(orgId: OrgId, lang: Lang) {
        defaults.forEach { (name, description) ->
            tr
                .sql("INSERT INTO lead_sources (id, org_id, name, description) VALUES (:id, :orgId, :name, :description)")
                .bind("id", LeadSourceId.new())
                .bind("orgId", orgId)
                .bind("name", name.localize(lang))
                .bind("description", description.localize(lang))
                .execute()
        }
    }
}
