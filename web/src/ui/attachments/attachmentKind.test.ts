import { describe, expect, it } from "vitest";
import { previewKind } from "./attachmentKind";

describe("способ показа вложения", () => {
  it("изображения, которые показывает браузер, — без учёта регистра и параметров", () => {
    expect(previewKind("image/jpeg")).toBe("image");
    expect(previewKind("image/JPEG; charset=x")).toBe("image");
    expect(previewKind("image/svg+xml")).toBe("image");
  });

  it("изображения, которые браузер не показывает, — карточкой", () => {
    expect(previewKind("image/heic")).toBe("file");
    expect(previewKind("image/tiff")).toBe("file");
  });

  it("PDF, видео и аудио", () => {
    expect(previewKind("application/pdf")).toBe("pdf");
    expect(previewKind("video/mp4")).toBe("video");
    expect(previewKind("video/quicktime")).toBe("file");
    expect(previewKind("audio/mpeg")).toBe("audio");
  });

  it("всё остальное — карточкой", () => {
    expect(previewKind("text/html")).toBe("file");
    expect(previewKind("")).toBe("file");
  });
});
