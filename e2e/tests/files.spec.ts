import { call } from "../support/api";
import { createClient } from "../support/data";
import { expect, test } from "../support/fixtures";
import { t } from "../support/i18n";

/** PNG 1×1: файл-картинка, чтобы плитка вложения показала его по выданной ссылке. */
const PIXEL = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
  "base64",
);

test("загруженный файл отдаётся по выданной ссылке", async ({ page, owner }) => {
  await call(owner.api, "org/client-card/update", {
    tasks: false,
    notes: false,
    chat: false,
    documents: true,
  });
  const clientId = await createClient(owner.api, "Галина");

  await page.goto(`/clients/${clientId}`);
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: t["clients.detail.uploadDocument"] }).click();
  await (await chooser).setFiles({ name: "pixel.png", mimeType: "image/png", buffer: PIXEL });

  const tile = page.getByRole("button", {
    name: t["attachments.open"].replace("{name}", "pixel.png"),
  });
  const image = tile.locator("img");
  await expect(image).toHaveAttribute("src", /^https:\/\/minio\.e2e\.test\//);
  const url = await image.getAttribute("src");
  expect(url).not.toBeNull();

  const download = await page.request.get(url ?? "");
  expect(download.status()).toBe(200);
  expect(Buffer.compare(await download.body(), PIXEL)).toBe(0);
});
