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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import org.athletica.crm.generated.resources.auth_already_have_account
import org.athletica.crm.generated.resources.error_registration_failed
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.generated.resources.label_email
import org.athletica.crm.generated.resources.label_org_name
import org.athletica.crm.generated.resources.label_password
import org.athletica.crm.generated.resources.label_timezone
import org.athletica.crm.generated.resources.label_your_name
import org.athletica.crm.generated.resources.screen_register
import org.athletica.crm.platformAvailableTimezones
import org.jetbrains.compose.resources.stringResource

/**
 * Экран регистрации новой организации.
 *
 * [state] — текущее состояние экрана (ошибка, загрузка),
 * [timezone] — выбранный часовой пояс,
 * [onRegister] — callback кнопки "Зарегистрироваться",
 * [onTimezoneChange] — callback изменения часового пояса,
 * [onErrorDismissed] — вызывается после того, как snackbar скрыт,
 * [onNavigateToLogin] — вызывается при нажатии "Войти".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    state: RegisterState = RegisterState.Idle,
    timezone: String = "",
    onRegister: (RegisterForm) -> Unit = {},
    onTimezoneChange: (String) -> Unit = {},
    onErrorDismissed: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var form by remember { mutableStateOf(RegisterForm()) }

    val availableZones = remember { platformAvailableTimezones() }
    var timezoneExpanded by remember { mutableStateOf(false) }
    var timezoneQuery by remember { mutableStateOf("") }
    val filteredZones =
        remember(timezoneQuery) {
            if (timezoneQuery.isEmpty()) {
                availableZones
            } else {
                availableZones.filter { it.contains(timezoneQuery, ignoreCase = true) }
            }
        }

    val errorMessage: String? =
        if (state is RegisterState.Error) {
            when (val error = state.error) {
                is RegisterError.RegistrationFailed -> stringResource(Res.string.error_registration_failed)
                is RegisterError.ServiceUnavailable -> stringResource(Res.string.error_service_unavailable)
                is RegisterError.ServerValidation -> error.message
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

    val isFormValid = form.isValid

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
                    text = stringResource(Res.string.screen_register),
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = form.organizationName,
                    onValueChange = { form = form.copy(organizationName = it) },
                    label = { Text(stringResource(Res.string.label_org_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it) },
                    label = { Text(stringResource(Res.string.label_your_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = form.email,
                    onValueChange = { form = form.copy(email = it) },
                    label = { Text(stringResource(Res.string.label_email)) },
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
                    value = form.password,
                    onValueChange = { form = form.copy(password = it) },
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
                                if (isFormValid) onRegister(form)
                            },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = timezoneExpanded,
                    onExpandedChange = { expanded ->
                        timezoneExpanded = expanded
                        if (expanded) timezoneQuery = "" else timezoneQuery = ""
                    },
                ) {
                    OutlinedTextField(
                        value = if (timezoneExpanded) timezoneQuery else timezone,
                        onValueChange = { query ->
                            timezoneQuery = query
                            timezoneExpanded = true
                        },
                        label = { Text(stringResource(Res.string.label_timezone)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timezoneExpanded) },
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = timezoneExpanded,
                        onDismissRequest = { timezoneExpanded = false },
                    ) {
                        filteredZones.take(100).forEach { zone ->
                            DropdownMenuItem(
                                text = { Text(zone) },
                                onClick = {
                                    onTimezoneChange(zone)
                                    timezoneExpanded = false
                                },
                            )
                        }
                    }
                }

                Button(
                    onClick = { onRegister(form) },
                    enabled = isFormValid && state !is RegisterState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (state) {
                        is RegisterState.Loading ->
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        else -> Text(stringResource(Res.string.action_register))
                    }
                }

                val primary = MaterialTheme.colorScheme.primary
                Text(
                    text =
                        buildAnnotatedString {
                            append(stringResource(Res.string.auth_already_have_account) + " ")
                            withLink(LinkAnnotation.Clickable(tag = "login") { onNavigateToLogin() }) {
                                withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                                    append(stringResource(Res.string.action_login))
                                }
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
