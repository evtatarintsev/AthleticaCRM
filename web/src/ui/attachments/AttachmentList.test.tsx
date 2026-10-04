import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UploadResponseSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { renderPage } from "@/test/render";
import { AttachmentList, type AttachmentItem } from "./AttachmentList";

/** Вложение [name] с типом [contentType] и размером [sizeBytes]. */
function attachment(name: string, contentType: string, sizeBytes = 1024): AttachmentItem {
  const file = UploadResponseSchema.parse({
    id: `0199a0b2-7c3e-7d2a-9f10-${String(name.length).padStart(12, "0")}`,
    url: `https://files.example/${name}`,
    originalName: name,
    contentType,
    sizeBytes,
  });
  return { key: name, name, file };
}

const photo = attachment("photo.jpg", "image/jpeg");
const contract = attachment("contract.pdf", "application/pdf", 245_760);
const report = attachment("report.xlsx", "application/vnd.ms-excel");

/** Подпись кнопки открытия вложения [name]. */
const openLabel = (name: string) => `Открыть «${name}»`;

/** Открытый просмотрщик. */
const viewer = async () =>
  within(await screen.findByRole("dialog", { name: ru["attachments.viewerTitle"] }));

afterEach(() => {
  Reflect.deleteProperty(navigator, "pdfViewerEnabled");
});

describe("список вложений", () => {
  it("изображение — миниатюрой с подписью, PDF и HEIC — карточками с размером", async () => {
    const heic = attachment("IMG_0001.heic", "image/heic");
    renderPage(<AttachmentList items={[photo, contract, heic]} />);

    const tile = await screen.findByRole("button", { name: openLabel("photo.jpg") });
    expect(tile.querySelector("img")).toHaveAttribute("src", "https://files.example/photo.jpg");
    expect(screen.getByText("photo.jpg")).toBeVisible();
    const pdf = screen.getByRole("button", { name: openLabel("contract.pdf") });
    expect(pdf.querySelector("img")).toBeNull();
    expect(within(pdf).getByText("240 КБ")).toBeVisible();
    expect(
      screen.getByRole("button", { name: openLabel("IMG_0001.heic") }).querySelector("img"),
    ).toBeNull();
  });

  it("незагрузившаяся миниатюра заменяется карточкой", async () => {
    renderPage(<AttachmentList items={[photo]} />);
    const tile = await screen.findByRole("button", { name: openLabel("photo.jpg") });
    const image = tile.querySelector("img");
    expect(image).not.toBeNull();
    if (image !== null) {
      fireEvent.error(image);
    }

    expect(tile.querySelector("img")).toBeNull();
    expect(within(tile).getByText("1 КБ")).toBeVisible();
  });

  it("удаление вызывает обработчик и не открывает просмотрщик", async () => {
    const onRemove = vi.fn();
    renderPage(
      <AttachmentList
        items={[photo]}
        onRemove={onRemove}
        removeLabel={(name) => `Убрать «${name}»`}
      />,
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Убрать «photo.jpg»" }));

    expect(onRemove).toHaveBeenCalledWith(photo);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("без обработчика удаления кнопки удаления нет", async () => {
    renderPage(<AttachmentList items={[photo]} />);
    await screen.findByRole("button", { name: openLabel("photo.jpg") });
    expect(screen.getAllByRole("button")).toHaveLength(1);
  });

  it("вложение без загруженных данных не нажимается", async () => {
    renderPage(<AttachmentList items={[{ key: "doc", name: "Справка", file: null }]} />);
    expect(await screen.findByRole("button", { name: openLabel("Справка") })).toBeDisabled();
  });
});

describe("просмотрщик вложений", () => {
  it("открывается кликом и Enter с именем и позицией", async () => {
    renderPage(<AttachmentList items={[contract, photo, report]} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: openLabel("photo.jpg") }));
    const dialog = await viewer();
    expect(dialog.getByText("2 из 3")).toBeVisible();
    expect(dialog.getByRole("img", { name: "photo.jpg" })).toHaveAttribute(
      "src",
      "https://files.example/photo.jpg",
    );
    expect(dialog.getByRole("link", { name: ru["attachments.openInNewTab"] })).toHaveAttribute(
      "href",
      "https://files.example/photo.jpg",
    );

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    screen.getByRole("button", { name: openLabel("report.xlsx") }).focus();
    await user.keyboard("{Enter}");
    expect((await viewer()).getByText("3 из 3")).toBeVisible();
  });

  it("переходит стрелками и кнопками, не выходя за границы", async () => {
    renderPage(<AttachmentList items={[photo, contract, report]} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: openLabel("photo.jpg") }));
    const dialog = await viewer();
    expect(dialog.getByRole("button", { name: ru["attachments.previous"] })).toBeDisabled();
    await user.keyboard("{ArrowRight}");
    expect(dialog.getByText("2 из 3")).toBeVisible();
    await user.click(dialog.getByRole("button", { name: ru["attachments.next"] }));
    expect(dialog.getByText("3 из 3")).toBeVisible();
    expect(dialog.getByRole("button", { name: ru["attachments.next"] })).toBeDisabled();
    await user.keyboard("{ArrowRight}");
    expect(dialog.getByText("3 из 3")).toBeVisible();
    await user.keyboard("{ArrowLeft}");
    expect(dialog.getByText("2 из 3")).toBeVisible();
  });

  it("при одном вложении кнопок перехода нет", async () => {
    renderPage(<AttachmentList items={[photo]} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: openLabel("photo.jpg") }));
    const dialog = await viewer();
    expect(dialog.queryByRole("button", { name: ru["attachments.previous"] })).toBeNull();
    expect(dialog.queryByRole("button", { name: ru["attachments.next"] })).toBeNull();
  });

  it("PDF показывается встроенно, а без встроенного просмотра — карточкой", async () => {
    renderPage(<AttachmentList items={[contract]} />);
    const user = userEvent.setup();
    const tile = await screen.findByRole("button", { name: openLabel("contract.pdf") });

    await user.click(tile);
    await viewer();
    expect(document.body.querySelector("iframe")).toHaveAttribute(
      "src",
      "https://files.example/contract.pdf",
    );
    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    Object.defineProperty(navigator, "pdfViewerEnabled", { value: false, configurable: true });
    await user.click(tile);
    const dialog = await viewer();
    expect(document.body.querySelector("iframe")).toBeNull();
    expect(dialog.getByText("240 КБ")).toBeVisible();
  });

  it("видео — плеером без автозапуска", async () => {
    const video = attachment("training.mp4", "video/mp4");
    renderPage(<AttachmentList items={[video]} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: openLabel("training.mp4") }));
    await viewer();
    const player = document.body.querySelector("video");
    expect(player).toHaveAttribute("src", "https://files.example/training.mp4");
    expect(player).toHaveAttribute("controls");
    expect(player).not.toHaveAttribute("autoplay");
  });

  it("прочий файл — карточкой со ссылкой на файл", async () => {
    renderPage(<AttachmentList items={[report]} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: openLabel("report.xlsx") }));
    const dialog = await viewer();
    expect(dialog.getByText("1 КБ")).toBeVisible();
    for (const link of dialog.getAllByRole("link", { name: ru["attachments.openInNewTab"] })) {
      expect(link).toHaveAttribute("href", "https://files.example/report.xlsx");
      expect(link).toHaveAttribute("target", "_blank");
    }
  });

  it("незагрузившееся изображение показывается карточкой", async () => {
    renderPage(<AttachmentList items={[photo]} />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: openLabel("photo.jpg") }));
    const dialog = await viewer();
    fireEvent.error(dialog.getByRole("img", { name: "photo.jpg" }));

    expect(dialog.queryByRole("img", { name: "photo.jpg" })).toBeNull();
    expect(dialog.getByText("1 КБ")).toBeVisible();
  });

  it("Esc закрывает просмотрщик и возвращает фокус на плитку", async () => {
    renderPage(<AttachmentList items={[photo, contract]} />);
    const user = userEvent.setup();
    const tile = await screen.findByRole("button", { name: openLabel("photo.jpg") });

    await user.click(tile);
    await viewer();
    await user.keyboard("{ArrowRight}");
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(tile).toHaveFocus();
  });
});
