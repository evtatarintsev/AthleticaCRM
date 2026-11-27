package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.athletica.mobile.api.ApiError
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.signIn_changePhone
import org.athletica.mobile.resources.signIn_codeLabel
import org.athletica.mobile.resources.signIn_codeSent
import org.athletica.mobile.resources.signIn_codeSentSms
import org.athletica.mobile.resources.signIn_codeTitle
import org.athletica.mobile.resources.signIn_getCode
import org.athletica.mobile.resources.signIn_login
import org.athletica.mobile.resources.signIn_phoneHint
import org.athletica.mobile.resources.signIn_phoneLabel
import org.athletica.mobile.resources.signIn_phoneTitle
import org.athletica.mobile.resources.signIn_resend
import org.athletica.mobile.resources.signIn_retryIn
import org.athletica.mobile.resources.signIn_termsAccept
import org.athletica.mobile.resources.signIn_termsText
import org.athletica.mobile.resources.signIn_termsTitle
import org.athletica.mobile.resources.signIn_viaSms
import org.jetbrains.compose.resources.stringResource

/** Шаг входа: номер, код или принятие условий перед повтором входа. */
private sealed interface Step {
    /** Ввод номера. */
    data object Phone : Step

    /**
     * Ввод кода, отправленного на [phone] (по SMS, если [viaSms]); [termsVersion] — версия условий
     * для повтора входа, [retryAfterSeconds] и [issue] — отсчёт до повтора с номером запроса.
     * [terms] — сервер попросил принять условия.
     */
    data class Code(
        val phone: String,
        val viaSms: Boolean,
        val termsVersion: String,
        val retryAfterSeconds: Long,
        val issue: Int,
        val terms: Boolean = false,
    ) : Step
}

/**
 * Вход по номеру и одноразовому коду через [api]. Условия показываются, только если сервер
 * ответил `CLIENT_APP_TERMS_REQUIRED`: тогда вход повторяется с принятой версией. [onSignedIn] — сеанс сохранён.
 */
@Composable
fun SignInScreen(api: ClientApi, onSignedIn: () -> Unit) {
    var step by remember { mutableStateOf<Step>(Step.Phone) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requestCode(target: String, viaSms: Boolean) {
        scope.launch {
            busy = true
            error = null
            api.requestCode(target, viaSms).fold(
                { error = it },
                {
                    code = ""
                    val issue = (step as? Step.Code)?.issue?.plus(1) ?: 0
                    step = Step.Code(target, viaSms, it.termsVersion, it.retryAfterSeconds, issue)
                },
            )
            busy = false
        }
    }

    fun login(current: Step.Code, acceptedTermsVersion: String?) {
        scope.launch {
            busy = true
            error = null
            api.login(current.phone, code, acceptedTermsVersion).fold(
                {
                    if (it is ApiError.Business && it.code == "CLIENT_APP_TERMS_REQUIRED") {
                        step = current.copy(terms = true)
                    } else {
                        step = current.copy(terms = false)
                        error = it
                    }
                },
                { onSignedIn() },
            )
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (val current = step) {
            Step.Phone -> {
                Text(stringResource(Res.string.signIn_phoneTitle), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.signIn_phoneHint))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(Res.string.signIn_phoneLabel)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { ErrorText(it) }
                Button(onClick = { requestCode(phone, false) }, enabled = !busy && phone.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.signIn_getCode))
                }
            }
            is Step.Code if current.terms -> {
                Text(stringResource(Res.string.signIn_termsTitle), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.signIn_termsText))
                error?.let { ErrorText(it) }
                Button(onClick = { login(current, current.termsVersion) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.signIn_termsAccept))
                }
            }
            is Step.Code -> {
                Text(stringResource(Res.string.signIn_codeTitle), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (current.viaSms) Res.string.signIn_codeSentSms else Res.string.signIn_codeSent, current.phone))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(Res.string.signIn_codeLabel)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { ErrorText(it) }
                Button(onClick = { login(current, null) }, enabled = !busy && code.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.signIn_login))
                }
                Resend(current, busy) { requestCode(current.phone, it) }
                TextButton(onClick = {
                    error = null
                    step = Step.Phone
                }) { Text(stringResource(Res.string.signIn_changePhone)) }
            }
        }
    }
}

/**
 * Повтор кода шага [step]: отсчёт до повтора, затем «Отправить ещё раз» и «Отправить по SMS».
 * [onResend] получает признак SMS; новый запрос перезапускает отсчёт.
 */
@Composable
private fun Resend(step: Step.Code, busy: Boolean, onResend: (viaSms: Boolean) -> Unit) {
    var left by remember(step.issue) { mutableStateOf(step.retryAfterSeconds) }
    LaunchedEffect(step.issue) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    if (left > 0) {
        Text(stringResource(Res.string.signIn_retryIn, "${left / 60}:${(left % 60).toString().padStart(2, '0')}"), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onResend(false) }, enabled = !busy) { Text(stringResource(Res.string.signIn_resend)) }
        OutlinedButton(onClick = { onResend(true) }, enabled = !busy) { Text(stringResource(Res.string.signIn_viaSms)) }
    }
}

/** Текст ошибки [error] цветом ошибки. */
@Composable
fun ErrorText(error: ApiError) {
    Text(error.text(), color = MaterialTheme.colorScheme.error)
}
