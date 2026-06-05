package org.athletica.crm.components.messaging

import androidx.compose.runtime.Composable
import org.athletica.crm.api.schemas.messaging.DeliveryStateSchema
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.channel_type_email
import org.athletica.crm.generated.resources.channel_type_in_app
import org.athletica.crm.generated.resources.channel_type_max
import org.athletica.crm.generated.resources.channel_type_sms
import org.athletica.crm.generated.resources.channel_type_telegram
import org.athletica.crm.generated.resources.channel_type_vk
import org.athletica.crm.generated.resources.channel_type_whatsapp
import org.athletica.crm.generated.resources.msg_status_delivered
import org.athletica.crm.generated.resources.msg_status_failed
import org.athletica.crm.generated.resources.msg_status_queued
import org.athletica.crm.generated.resources.msg_status_sent
import org.jetbrains.compose.resources.stringResource

/** Локализованное название типа канала. */
@Composable
fun ChannelType.label(): String =
    stringResource(
        when (this) {
            ChannelType.SMS -> Res.string.channel_type_sms
            ChannelType.TELEGRAM -> Res.string.channel_type_telegram
            ChannelType.WHATSAPP -> Res.string.channel_type_whatsapp
            ChannelType.EMAIL -> Res.string.channel_type_email
            ChannelType.MAX -> Res.string.channel_type_max
            ChannelType.VK -> Res.string.channel_type_vk
            ChannelType.IN_APP -> Res.string.channel_type_in_app
        },
    )

/** Локализованное название статуса доставки. */
@Composable
fun DeliveryStateSchema.label(): String =
    stringResource(
        when (this) {
            DeliveryStateSchema.PENDING -> Res.string.msg_status_queued
            DeliveryStateSchema.SENT -> Res.string.msg_status_sent
            DeliveryStateSchema.DELIVERED -> Res.string.msg_status_delivered
            DeliveryStateSchema.FAILED -> Res.string.msg_status_failed
        },
    )
