import { expect, type APIRequestContext } from "@playwright/test";
import { z } from "zod";

/** HTTP API перехватчика почты Mailpit в сети стенда. */
const MAILPIT = "http://mailpit:8025/api/v1";

/** Ответ поиска писем Mailpit; лишние поля отбрасываются. */
const SearchSchema = z.object({
  messages: z.array(z.object({ ID: z.string() })),
});

/** Письмо Mailpit: нужен только текст. */
const MessageSchema = z.object({ Text: z.string() });

/**
 * Ждёт письмо на адрес [to], текст которого совпадает с [pattern], и возвращает первую группу
 * совпадения (или всё совпадение, если групп нет). Письма уходят фоновой рассылкой раз в
 * несколько секунд, поэтому ожидание — опросом.
 */
export async function mailFor(
  request: APIRequestContext,
  to: string,
  pattern: RegExp,
): Promise<string> {
  let found = "";
  await expect
    .poll(
      async () => {
        found = await firstMatch(request, to, pattern);
        return found;
      },
      { message: `письмо на ${to} с ${String(pattern)}`, timeout: 45_000, intervals: [1_000] },
    )
    .not.toBe("");
  return found;
}

/** Первое совпадение [pattern] среди писем на [to] или пустая строка. */
async function firstMatch(
  request: APIRequestContext,
  to: string,
  pattern: RegExp,
): Promise<string> {
  const search = await request.get(`${MAILPIT}/search`, { params: { query: `to:"${to}"` } });
  const { messages } = SearchSchema.parse(await search.json());
  for (const { ID } of messages) {
    const message = await request.get(`${MAILPIT}/message/${ID}`);
    const match = pattern.exec(MessageSchema.parse(await message.json()).Text);
    if (match !== null) {
      return match[1] ?? match[0];
    }
  }
  return "";
}
