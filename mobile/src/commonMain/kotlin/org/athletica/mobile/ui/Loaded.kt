package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import arrow.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.athletica.mobile.api.ApiError
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.app_retry
import org.athletica.mobile.resources.error_offline
import org.jetbrains.compose.resources.stringResource

/** Состояние загрузки данных экрана. */
sealed interface LoadState<out T> {
    /** Идёт запрос. */
    data object Loading : LoadState<Nothing>

    /** Запрос не удался с ошибкой [error]. */
    data class Failed(val error: ApiError) : LoadState<Nothing>

    /** Данные [value] получены. */
    data class Ready<T>(val value: T) : LoadState<T>
}

/** View-модель загрузки данных экрана запросом [load]: состояние [state] и повтор [reload]. Данные не кэшируются между запусками. */
class Loader<T>(private val load: suspend () -> Either<ApiError, T>) : ViewModel() {
    private val mutableState = MutableStateFlow<LoadState<T>>(LoadState.Loading)

    /** Текущее состояние загрузки. */
    val state: StateFlow<LoadState<T>> = mutableState

    init {
        reload()
    }

    /** Загружает данные заново. */
    fun reload() {
        mutableState.value = LoadState.Loading
        viewModelScope.launch { mutableState.value = load().fold({ LoadState.Failed(it) }, { LoadState.Ready(it) }) }
    }
}

/** View-модель загрузки [load] экрана, своя для каждого ключа [key] (экран и профиль). */
@Composable
fun <T> rememberLoader(key: String, load: suspend () -> Either<ApiError, T>): Loader<T> = viewModel(key = key) { Loader(load) }

/**
 * Данные загрузчика [loader]: пока грузятся — индикатор, при ошибке — [Failure] с повтором,
 * готовые — [content].
 */
@Composable
fun <T> Loaded(loader: Loader<T>, content: @Composable (T) -> Unit) {
    val state by loader.state.collectAsState()
    when (val current = state) {
        LoadState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
        is LoadState.Failed -> Failure(current.error, loader::reload)
        is LoadState.Ready -> content(current.value)
    }
}

/** Ошибка [error] с кнопкой «Повторить» [onRetry]: без сети — «Нет связи с сервером». */
@Composable
fun Failure(error: ApiError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(error.text(), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text(stringResource(Res.string.app_retry)) }
    }
}

/** Текст ошибки для человека: бизнес-ошибку сервер уже перевёл. */
@Composable
fun ApiError.text(): String =
    when (this) {
        is ApiError.Business -> message
        ApiError.Unavailable -> stringResource(Res.string.error_offline)
    }
