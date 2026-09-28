## 1. Mock data layer

- [x] 1.1 Create `web/src/sessions/sessionDetailMock.ts`: `AttendanceStatus` type
      (`PRESENT`/`ABSENT`/`SICK`/`LATE`), `MockSessionDetail` type (group, date, time, hall, status,
      coaches, notes, participants with attendance), and `mockSessionDetail(sessionId: SessionId):
      MockSessionDetail` that deterministically varies status/data by `sessionId` across at least
      2-3 fixtures; verify with a unit test that the same id always returns the same fixture and that
      different ids can return different statuses.
- [x] 1.2 Add pure state-transition helpers on `MockSessionDetail` (cancel, reschedule, change hall,
      change coaches, set note, set participant attendance) following the "one state object,
      `copy`-style transitions" convention; verify with unit tests for each transition.

## 2. Session detail page

- [x] 2.1 Create `web/src/sessions/SessionDetailPage.tsx`: header (group, date/time, hall, status
      badge), coaches list, participants list with `AttendanceToggle` per participant; verify by
      rendering with a fixed `sessionId` and asserting the fields from task 1.1's fixture appear.
- [x] 2.2 Create `web/src/sessions/AttendanceToggle.tsx`: four-way toggle (был/не был/болел/опоздал)
      for one participant, calling back with the new status; verify with a test that clicking each
      option calls the callback with the matching `AttendanceStatus`.
- [x] 2.3 Wire action buttons (отменить, перенести, сменить зал, сменить тренеров, оставить заметку)
      to the state-transition helpers from 1.2; disable/hide отменить, перенести, сменить зал, сменить
      тренеров when status is `COMPLETED` or `CANCELLED`, per spec scenario "Открытие карточки
      отменённого занятия"; verify with a test that clicking "Отменить" flips the status badge and
      that action buttons are absent/disabled on a cancelled fixture.
- [x] 2.4 Add "Назад" navigation control; verify it links back to `/schedule`.
- [x] 2.5 Add i18n strings used by the page to `web/src/i18n/ru.ts` and `en.ts`; verify
      `npm run check` reports no missing-key lint errors.

## 3. Routing and entry points

- [x] 3.1 Add `/sessions/$sessionId` route in `web/src/app/router.tsx` following the `taskRoute`
      pattern (`SessionIdSchema.safeParse` in `params.parse`); verify an invalid id in the URL 404s
      instead of rendering the page.
- [x] 3.2 Make `SessionCard` in `web/src/schedule/ScheduleWeekGrid.tsx` a link to
      `/sessions/$sessionId` using `session.id`; verify by clicking a session card in a test/story and
      asserting navigation to the session route.
- [x] 3.3 Make each session row in `web/src/home/SessionsWidgetCard.tsx` a link to
      `/sessions/$sessionId` using `session.sessionId`; verify the same way.

## 4. Verification

- [x] 4.1 Run `npm run format` and `npm run check` inside `web/` and confirm both pass.
- [x] 4.2 Manually exercise the page in the browser preview: open from schedule, open from home,
      toggle attendance for a participant, trigger each action button, confirm no network request is
      made (check the network panel) and confirm behavior for a cancelled-fixture session (actions
      hidden/disabled).
