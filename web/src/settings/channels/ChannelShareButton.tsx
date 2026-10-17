import { CopyIcon, DownloadIcon, Share2Icon } from "lucide-react";
import { useRef, useState } from "react";
import QRCode from "react-qr-code";
import { toast } from "sonner";
import type { ChannelShareSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useI18n } from "@/i18n/context";

/** Сторона QR-кода на экране, px. */
const QR_SIZE = 192;

/** Сторона PNG-файла QR-кода с белыми полями, px: достаточно для печати. */
const PNG_SIZE = 1024;

/** Ширина белого поля вокруг QR-кода в PNG, px. */
const PNG_MARGIN = 64;

/**
 * Кнопка «Поделиться» каналом [name]: открывает диалог со ссылкой [share], её копированием,
 * QR-кодом с выгрузкой в PNG и системным меню «Поделиться», если браузер его поддерживает.
 * Зависит только от данных раздачи, поэтому годится для любого экрана со списком каналов.
 */
export function ChannelShareButton({
  name,
  share,
}: {
  readonly name: string;
  readonly share: ChannelShareSchema;
}) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const qr = useRef<HTMLDivElement>(null);
  const canShare = "share" in navigator;

  const copy = async (): Promise<void> => {
    try {
      await navigator.clipboard.writeText(share.url);
      toast.success(t("channels.linkCopied"));
    } catch {
      toast.error(t("channels.linkCopyFailed"));
    }
  };

  const download = async (): Promise<void> => {
    const svg = qr.current?.querySelector("svg") ?? null;
    const png = svg === null ? null : await qrPng(svg);
    if (png === null) {
      toast.error(t("channels.downloadQrFailed"));
      return;
    }
    const link = document.createElement("a");
    link.href = URL.createObjectURL(png);
    link.download = `${fileName(name)}.png`;
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const systemShare = async (): Promise<void> => {
    try {
      await navigator.share({ title: name, url: share.url });
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) {
        toast.error(t("channels.systemShareFailed"));
      }
    }
  };

  return (
    <>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t("channels.shareChannel", { name })}
        onClick={() => {
          setOpen(true);
        }}
      >
        <Share2Icon aria-hidden />
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent closeLabel={t("action.close")}>
          <DialogHeader>
            <DialogTitle>{t("channels.shareTitle", { name })}</DialogTitle>
            <DialogDescription>{t("channels.shareText")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1">
              <p className="text-sm font-medium">{t("channels.link")}</p>
              <div className="flex items-center gap-2">
                <a
                  href={share.url}
                  target="_blank"
                  rel="noreferrer"
                  className="min-w-0 flex-1 truncate text-sm text-primary underline-offset-4 hover:underline"
                >
                  {share.url}
                </a>
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label={t("channels.copyLink")}
                  onClick={() => {
                    void copy();
                  }}
                >
                  <CopyIcon aria-hidden />
                </Button>
              </div>
            </div>
            <div ref={qr} className="flex justify-center rounded-lg border bg-white p-4">
              <QRCode
                value={share.url}
                size={QR_SIZE}
                title={t("channels.qrCode", { url: share.url })}
                role="img"
                aria-label={t("channels.qrCode", { url: share.url })}
              />
            </div>
          </div>
          <DialogFooter closeLabel={t("action.close")}>
            {canShare && (
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  void systemShare();
                }}
              >
                <Share2Icon aria-hidden />
                {t("channels.systemShare")}
              </Button>
            )}
            <Button
              type="button"
              onClick={() => {
                void download();
              }}
            >
              <DownloadIcon aria-hidden />
              {t("channels.downloadQr")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

/** Имя файла из имени канала [name]: без символов, недопустимых в именах файлов. */
function fileName(name: string): string {
  const safe = name.replace(/[\\/:*?"<>|]+/g, " ").trim();
  return safe === "" ? "qr" : safe;
}

/**
 * PNG из QR-кода [svg] на белом фоне с полями; `null`, если браузер не смог отрисовать
 * изображение.
 */
async function qrPng(svg: SVGSVGElement): Promise<Blob | null> {
  const source = new XMLSerializer().serializeToString(svg);
  const url = URL.createObjectURL(new Blob([source], { type: "image/svg+xml" }));
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    const canvas = document.createElement("canvas");
    canvas.width = PNG_SIZE;
    canvas.height = PNG_SIZE;
    const context = canvas.getContext("2d");
    if (context === null) {
      return null;
    }
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, PNG_SIZE, PNG_SIZE);
    context.drawImage(
      image,
      PNG_MARGIN,
      PNG_MARGIN,
      PNG_SIZE - 2 * PNG_MARGIN,
      PNG_SIZE - 2 * PNG_MARGIN,
    );
    return await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, "image/png");
    });
  } catch {
    return null;
  } finally {
    URL.revokeObjectURL(url);
  }
}
