package org.athletica.crm.components.messaging

import androidx.compose.runtime.Composable
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.core.messaging.MessageStatus
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
import org.athletica.crm.generated.resources.msg_status_read
import org.athletica.crm.generated.resources.msg_status_sending
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

/** Локализованное название статуса сообщения. */
@Composable
fun MessageStatus.label(): String =
    stringResource(
        when (this) {
            MessageStatus.QUEUED -> Res.string.msg_status_queued
            MessageStatus.SENDING -> Res.string.msg_status_sending
            MessageStatus.SENT -> Res.string.msg_status_sent
            MessageStatus.DELIVERED -> Res.string.msg_status_delivered
            MessageStatus.READ -> Res.string.msg_status_read
            MessageStatus.FAILED -> Res.string.msg_status_failed
        },
    )
