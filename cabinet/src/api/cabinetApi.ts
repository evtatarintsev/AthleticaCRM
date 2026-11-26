import type { ApiClient } from "./client";
import type { endpoints } from "./generated/contracts";

/** Клиент API кабинета на сгенерированном манифесте. */
export type CabinetApi = ApiClient<typeof endpoints>;
