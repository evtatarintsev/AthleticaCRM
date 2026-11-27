/**
 * Подмена типов `@reduxjs/toolkit` для проверки `tsc` (через `paths` в `tsconfig.base.json`).
 *
 * Пакет приходит только как зависимость Recharts, его `index.d.mts` не проходит
 * `exactOptionalPropertyTypes` (см. «Исключения из строгих правил» в `web/README.md`).
 * Код `web/` пакет не импортирует; Recharts берёт из него лишь типы своего внутреннего
 * хранилища, они и описаны здесь. Новый импорт в Recharts уронит `tsc` — тогда дополнить.
 */
import type { Action, Dispatch, Middleware, Reducer, Store } from "redux";
import type { ThunkDispatch } from "redux-thunk";

export type { Action, Dispatch, Reducer, Store, ThunkDispatch };

/** Действие с произвольными дополнительными полями (устаревший `AnyAction` Redux). */
export type AnyAction = Action & Readonly<Record<string, unknown>>;

/** Действие с полезной нагрузкой, как в Redux Toolkit. */
export interface PayloadAction<P = void, T extends string = string> {
  readonly payload: P;
  readonly type: T;
}

/** Создатель действия с обязательной нагрузкой. */
export type ActionCreatorWithPayload<P, T extends string = string> = ((
  payload: P,
) => PayloadAction<P, T>) & { readonly type: T };

/** Создатель действия с необязательной нагрузкой. */
export type ActionCreatorWithOptionalPayload<P, T extends string = string> = ((
  payload?: P,
) => PayloadAction<P, T>) & { readonly type: T };

/** Создатель действия без нагрузки. */
export type ActionCreatorWithoutPayload<T extends string = string> = (() => PayloadAction<
  undefined,
  T
>) & { readonly type: T };

/** Создатель действия, нагрузка которого не выводится из редьюсера. */
export type ActionCreatorWithNonInferrablePayload<T extends string = string> = (<P>(
  payload: P,
) => PayloadAction<P, T>) & { readonly type: T };

/** Создатель действия с функцией подготовки нагрузки из аргументов [Args]. */
export type ActionCreatorWithPreparedPayload<
  Args extends readonly unknown[],
  P,
  T extends string = string,
  E = never,
  M = never,
> = ((...args: Args) => PayloadAction<P, T> & { readonly error?: E; readonly meta?: M }) & {
  readonly type: T;
};

/** Срез хранилища Redux Toolkit; поля — те, что видны в типах Recharts. */
export interface Slice<
  State = unknown,
  CaseReducers = unknown,
  Name extends string = string,
  ReducerPath extends string = Name,
  Selectors = unknown,
> {
  readonly name: Name;
  readonly reducerPath: ReducerPath;
  readonly reducer: Reducer<State>;
  readonly actions: Readonly<Record<string, unknown>>;
  readonly caseReducers: CaseReducers;
  readonly selectors: Selectors;
  readonly getInitialState: () => State;
}

/** Слушающий middleware Redux Toolkit; Recharts использует только `middleware`. */
export interface ListenerMiddlewareInstance<
  State = unknown,
  DispatchType extends Dispatch = ThunkDispatch<State, unknown, AnyAction>,
  ExtraArgument = unknown,
> {
  readonly middleware: Middleware<object, State, DispatchType>;
  readonly clearListeners: () => void;
  readonly extra?: ExtraArgument;
}
