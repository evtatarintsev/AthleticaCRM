package org.athletica.crm.components.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_login
import org.athletica.crm.generated.resources.action_register
import org.athletica.crm.generated.resources.app_name
import org.athletica.crm.generated.resources.auth_no_account
import org.athletica.crm.generated.resources.error_invalid_credentials
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.generated.resources.label_login
import org.athletica.crm.generated.resources.label_password
import org.jetbrains.compose.resources.stringResource

/**
 * Экран авторизации с полями логина и пароля.
 * Ошибка входа отображается через [Snackbar].
 *
 * [state] — текущее состояние экрана (ошибка, загрузка),
 * [onLogin] — вызывается при нажатии "Войти", принимает логин и пароль,
 * [onErrorDismissed] — вызывается после того, как snackbar скрыт,
 * [onNavigateToRegister] — вызывается при нажатии "Зарегистрироваться".
 */
@Composable
fun LoginScreen(
    state: LoginState = LoginState.Idle,
    onLogin: (login: String, password: String) -> Unit = { _, _ -> },
    onErrorDismissed: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val errorMessage: String? =
        if (state is LoginState.Error) {
            when (val error = state.error) {
                is LoginError.InvalidCredentials -> stringResource(Res.string.error_invalid_credentials)
                is LoginError.ServiceUnavailable -> stringResource(Res.string.error_service_unavailable)
                is LoginError.ServerValidation -> error.message
            }
        } else {
            null
        }

    LaunchedEffect(state) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(320.dp),
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text(stringResource(Res.string.label_login)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.label_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                onLogin(login, password)
                            },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = { onLogin(login, password) },
                    enabled = login.isNotBlank() && password.isNotBlank() && state !is LoginState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (state) {
                        is LoginState.Loading ->
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        else -> Text(stringResource(Res.string.action_login))
                    }
                }

                val primary = MaterialTheme.colorScheme.primary
                Text(
                    text =
                        buildAnnotatedString {
                            append(stringResource(Res.string.auth_no_account) + " ")
                            withLink(LinkAnnotation.Clickable(tag = "register") { onNavigateToRegister() }) {
                                withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                                    append(stringResource(Res.string.action_register))
                                }
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
