/** Типы блоков ленты карточки клиента. */
export const BLOCK_TYPES = [
  "upcoming",
  "subscriptions",
  "groups",
  "payments",
  "visits",
  "missed",
  "tasks",
  "messages",
  "notes",
  "documents",
] as const;

/** Тип блока ленты. */
export type ClientCardBlockType = (typeof BLOCK_TYPES)[number];

/**
 * Блок ленты карточки — сущность со своими настройками (паритет с виджетами главной):
 * [id] даёт идентичность, [limit] — сколько записей показывать до «Все».
 * Видимость и порядок — не свойства блока, а раскладка шаблона.
 */
export interface ClientCardBlock {
  /** Идентификатор блока в шаблоне. */
  readonly id: string;
  /** Тип блока. */
  readonly type: ClientCardBlockType;
  /** Сколько записей показывать в блоке до «Все». */
  readonly limit: number;
}

/**
 * Шаблон карточки организации: пул блоков и раскладка (показанные блоки в порядке ленты).
 * Пока не хранится на сервере — используется [defaultTemplate]; настройка шаблона
 * организацией появится вместе с хранением.
 */
export interface ClientCardTemplate {
  /** Пул блоков с настройками. */
  readonly blocks: readonly ClientCardBlock[];
  /** Идентификаторы показанных блоков в порядке ленты. */
  readonly layout: readonly string[];
}

/** Шаблон по умолчанию: все блоки в порядке [BLOCK_TYPES], по 5 записей. */
export function defaultTemplate(): ClientCardTemplate {
  const blocks = BLOCK_TYPES.map((type) => ({ id: type, type, limit: 5 }));
  return { blocks, layout: blocks.map((block) => block.id) };
}

/** Показанные блоки [template] в порядке раскладки. */
export function visibleBlocks(template: ClientCardTemplate): readonly ClientCardBlock[] {
  const byId = new Map(template.blocks.map((block) => [block.id, block] as const));
  return template.layout.flatMap((id) => {
    const block = byId.get(id);
    return block === undefined ? [] : [block];
  });
}
