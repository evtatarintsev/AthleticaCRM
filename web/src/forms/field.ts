/**
 * Поле формы TanStack Form в объёме, нужном обёрткам полей. `FieldApi` из `form.Field`
 * подходит структурно, поэтому обёртки не зависят от generic-параметров формы.
 */
export interface BoundField<T> {
  /** Путь поля в значениях формы. */
  readonly name: string;
  /** Состояние поля: значение и результаты проверок. */
  readonly state: {
    readonly value: T;
    readonly meta: { readonly errors: readonly unknown[] };
  };
  /** Записывает новое значение поля; свойство, а не метод, — чтобы тип значения проверялся строго. */
  readonly handleChange: (value: T) => void;
  /** Отмечает, что поле потеряло фокус. */
  readonly handleBlur: () => void;
}

/**
 * [BoundField] вне TanStack Form: значение и обработчик изменения без формы целиком.
 * Нужен для полей внутри динамического списка (контакты, значения кастомных полей),
 * где элементы хранятся в обычном состоянии, а не в отдельных полях формы.
 */
export function manualField<T>(
  name: string,
  value: T,
  onChange: (value: T) => void,
): BoundField<T> {
  return {
    name,
    state: { value, meta: { errors: [] } },
    handleChange: onChange,
    handleBlur: () => undefined,
  };
}

/**
 * Первое сообщение об ошибке поля [field]. Ошибки zod-схемы приходят объектами
 * с `message`, ошибки функций-валидаторов — строками.
 */
export function fieldError<T>(field: BoundField<T>): string | null {
  for (const error of field.state.meta.errors) {
    if (typeof error === "string" && error !== "") {
      return error;
    }
    if (typeof error === "object" && error !== null && "message" in error) {
      const { message } = error;
      if (typeof message === "string" && message !== "") {
        return message;
      }
    }
  }
  return null;
}
