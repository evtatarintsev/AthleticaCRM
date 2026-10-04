import { useRef, useState } from "react";
import { AttachmentTile, type AttachmentItem } from "./AttachmentTile";
import { AttachmentViewer } from "./AttachmentViewer";

export type { AttachmentItem } from "./AttachmentTile";

/**
 * Вложения [items] сеткой плиток в исходном порядке; нажатие на плитку открывает
 * просмотрщик поверх страницы. Данные о файлах компонент получает готовыми и сам их
 * не запрашивает. Если передан [onRemove], у плиток есть кнопка удаления с подписью
 * [removeLabel]; подтверждение удаления — забота вызывающего.
 */
export function AttachmentList({
  items,
  onRemove,
  removeLabel,
}: {
  readonly items: readonly AttachmentItem[];
  readonly onRemove?: (item: AttachmentItem) => void;
  readonly removeLabel?: (name: string) => string;
}) {
  const [opened, setOpened] = useState<number | null>(null);
  const [broken, setBroken] = useState<ReadonlySet<string>>(new Set());
  const tiles = useRef(new Map<string, HTMLButtonElement>());
  const openedFrom = useRef<string | null>(null);

  const index = opened === null || items.length === 0 ? null : Math.min(opened, items.length - 1);

  const markBroken = (key: string) => {
    setBroken((current) => (current.has(key) ? current : new Set([...current, key])));
  };

  return (
    <>
      <ul className="grid grid-cols-[repeat(auto-fill,minmax(6rem,1fr))] gap-3">
        {items.map((item, position) => (
          <AttachmentTile
            key={item.key}
            item={item}
            broken={broken.has(item.key)}
            buttonRef={(node) => {
              if (node !== null) {
                tiles.current.set(item.key, node);
              }
              return () => {
                tiles.current.delete(item.key);
              };
            }}
            onOpen={() => {
              openedFrom.current = item.key;
              setOpened(position);
            }}
            onBroken={() => {
              markBroken(item.key);
            }}
            onRemove={
              onRemove === undefined
                ? undefined
                : () => {
                    onRemove(item);
                  }
            }
            removeLabel={removeLabel?.(item.name) ?? item.name}
          />
        ))}
      </ul>
      <AttachmentViewer
        items={items}
        index={index}
        broken={broken}
        onBroken={markBroken}
        onIndexChange={setOpened}
        onClose={() => {
          setOpened(null);
        }}
        onCloseAutoFocus={(event) => {
          const tile =
            openedFrom.current === null ? undefined : tiles.current.get(openedFrom.current);
          if (tile !== undefined) {
            event.preventDefault();
            tile.focus();
          }
        }}
      />
    </>
  );
}
