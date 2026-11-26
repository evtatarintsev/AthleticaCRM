import pg from "pg";

/**
 * Прямой доступ к Postgres стенда — только там, где интерфейс и API бессильны: кабинет нельзя
 * включить без настоящего Telegram Gateway или SMS-канала, а код входа не достать из Telegram или
 * SMS. Подключение — из переменных `PG*` раннера (оверлей стенда).
 */
async function withDb<T>(block: (client: pg.Client) => Promise<T>): Promise<T> {
  const client = new pg.Client();
  await client.connect();
  try {
    return await block(client);
  } finally {
    await client.end();
  }
}

/** Включает кабинет организации [companyName] в обход проверки способа доставки кодов. */
export async function enableCabinet(companyName: string): Promise<void> {
  await withDb((client) =>
    client.query("UPDATE organizations SET client_app_enabled = true WHERE name = $1", [
      companyName,
    ]),
  );
}

/**
 * Заменяет хэш последнего кода номера [phoneDigits] на хэш известного кода [code]: SHA-256 от
 * идентификатора строки и кода, как на сервере.
 */
export async function setKnownCode(phoneDigits: string, code: string): Promise<void> {
  const updated = await withDb((client) =>
    client.query(
      `UPDATE client_app_codes SET code_hash = encode(sha256(convert_to(id::text || $2, 'UTF8')), 'hex')
       WHERE id = (SELECT id FROM client_app_codes WHERE phone_digits = $1 ORDER BY created_at DESC LIMIT 1)`,
      [phoneDigits, code],
    ),
  );
  if (updated.rowCount !== 1) {
    throw new Error(`Код для номера ${phoneDigits} не найден`);
  }
}
