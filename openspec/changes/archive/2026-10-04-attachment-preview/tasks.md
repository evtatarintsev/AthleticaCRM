## 1. Основа

- [x] 1.1 Создать `web/src/ui/attachments/attachmentKind.ts` с чистой функцией `previewKind(contentType)` по design.md §2 (нижний регистр, отбрасывание параметров после `;`, явные списки image/pdf/video, `audio/*`, остальное — `file`). Проверить тестами в `attachmentKind.test.ts`: `image/JPEG; charset=x` → image; `image/heic`, `image/tiff` → file; `image/svg+xml` → image; `application/pdf` → pdf; `video/quicktime` → file; `audio/mpeg` → audio; `text/html` и пустая строка → file
- [x] 1.2 Создать `ui/attachments/fileSize.ts` с `fileSize(t, bytes)` по design.md §5: основание 1024, один знак после запятой, подписи единиц `attachments.size.*` из словаря. Проверить тестами в `fileSize.test.ts`: 0 и 512 байт — в байтах, 245 760 → «240 КБ» в `ru` и «240 KB» в `en`, 5,5 МБ и 2 ГБ — с одним знаком после запятой
- [x] 1.3 Добавить строки RU/EN `attachments.size.*`, `attachments.open`, `attachments.position`, `attachments.previous`, `attachments.next`, `attachments.openInNewTab`, `attachments.viewerTitle`. Проверить `npm run check` (typecheck словарей)

## 2. Компонент вложений

- [x] 2.1 Создать `FileCard.tsx`: иконка типа из `lucide-react` по `previewKind`, имя и размер через `fileSize`; два варианта — компактный для плитки и крупный для просмотрщика, у крупного ссылка «Открыть в новой вкладке» (`target="_blank" rel="noreferrer"`). Проверить typecheck и тестами из 2.3
- [x] 2.2 Создать `AttachmentTile.tsx` и `AttachmentList.tsx` по design.md §1 и §3. Тип `AttachmentItem` (`key`, `name`, `file: UploadResponse | null`); сетка `auto-fill` в исходном порядке. Плитка — `<button aria-label="Открыть <имя>">`: для image — `<img loading="lazy" decoding="async">`, иначе компактная `FileCard`. При `file === null` — `Skeleton`, плитка не нажимается. Ошибки миниатюр хранятся по `key` в `AttachmentList` и переключают плитку на `FileCard`. Кнопка удаления — соседний элемент, показывается только при `onRemove`, `aria-label` из `removeLabel(name)`
- [x] 2.3 Тесты `AttachmentList.test.tsx`: image-вложение даёт `img` с подписью, PDF — карточку с «240 КБ», `image/heic` — карточку. `fireEvent.error` на миниатюре заменяет её карточкой. Нажатие ✕ вызывает `onRemove` и не открывает просмотрщик. Без `onRemove` кнопки удаления нет. Элемент с `file: null` не нажимается
- [x] 2.4 Создать `AttachmentViewer.tsx` по design.md §4: управляемый Dialog почти на весь экран со скрытым заголовком `attachments.viewerTitle`, именем файла, позицией «N из M», ссылкой «Открыть в новой вкладке» и кнопкой закрытия. Контент рендерится с `key={item.key}`:
  - image — `<img object-contain>`;
  - pdf — `<iframe>` при `navigator.pdfViewerEnabled !== false`, иначе крупная `FileCard`;
  - video — `<video controls preload="metadata">`, audio — `<audio controls>`, без `autoplay`;
  - file, ошибка миниатюры из `AttachmentList` и `onError` контента — крупная `FileCard`.

  Кнопки «Предыдущее»/«Следующее» показываются только при числе вложений больше одного и недоступны на границах. ←/→ — обработчик `keydown` на `window` при открытом просмотрщике, игнорируется для `video`/`audio`. Фокус после закрытия возвращается на нажатую плитку через `onCloseAutoFocus`
- [x] 2.5 Подключить просмотрщик в `AttachmentList`: `index: number | null`. При сокращении списка индекс ограничивается его длиной, пустой список закрывает просмотрщик. Тесты в `AttachmentList.test.tsx`:
  - клик и Enter на плитке открывают просмотрщик с именем и «2 из 3»;
  - → и кнопка «Следующее» переходят к следующему вложению, на «3 из 3» → ничего не меняет;
  - при одном вложении нет кнопок перехода;
  - PDF даёт `iframe`, а при подменённом `navigator.pdfViewerEnabled = false` — карточку;
  - видео — `video` без `autoplay`;
  - `xlsx` — карточка со ссылкой на `url`;
  - `fireEvent.error` на изображении в просмотрщике даёт карточку;
  - Esc закрывает просмотрщик и возвращает фокус на плитку

## 3. Встраивание в экраны

- [x] 3.1 `TaskSheet.tsx`: заменить список ссылок на `AttachmentList` (`key: id`, `name: originalName`, `onRemove` → `detach`, `removeLabel` → `tasks.removeAttachment`). Обновить `TaskSheet.test.tsx`: прикреплённое изображение видно миниатюрой и открывается в просмотрщике; удаление шлёт `tasks/detach`; Esc в просмотрщике закрывает только его, карточка задачи остаётся открытой
- [x] 3.2 `TaskCreateSheet.tsx` (`NewAttachments`): заменить список на `AttachmentList`, `onRemove` убирает элемент из значения поля. Тест в `TaskCreateSheet.test.tsx`: после ввода заголовка и прикрепления `photo.png` миниатюра открывает просмотрщик; Esc закрывает только его, подтверждение потери изменений не показывается, заголовок сохранён
- [x] 3.3 `ClientDocumentsSection.tsx`: удалить `DocRow`. Собирать `items` через `useQueries(uploadInfoQuery)` (`key: doc.id`, `name: doc.name`, `file: data ?? null`), `onRemove` → `setDocToDelete`, `removeLabel` → `clients.detail.deleteDoc`. Обновить `ClientDetailPage.test.tsx`: документ-изображение видно миниатюрой с названием документа и открывается в просмотрщике; удаление по-прежнему спрашивает подтверждение

## 4. Проверка

- [x] 4.1 В `web/`: `npm run format` и `npm run check` (typecheck, eslint, prettier, vitest, type-coverage 100%) проходят без исключений из правил
- [ ] 4.2 Проверить в браузере через dev-сервер: карточка задачи с JPEG, PDF, MP4 и XLSX — миниатюры, просмотрщик, стрелки, Esc, «Открыть в новой вкладке»; то же в документах клиента. Ширина 375 px — сетка без горизонтальной прокрутки, просмотрщик на весь экран. Тёмная тема — читаемые подписи и кнопки
