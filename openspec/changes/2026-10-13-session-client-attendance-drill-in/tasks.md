## 1. Mock data layer

- [ ] 1.1 Create `web/src/sessions/attendanceMock.ts`: `AttendanceEntry` type (`sessionId`, `date`,
      `startTime`, `groupName`, `coachName`, `membershipName | null`, `status`, `comment`) and
      `mockClientAttendance(clientId: ClientId): readonly AttendanceEntry[]` that deterministically
      generates 10–15 entries sorted newest first, covering all four `AttendanceStatus` values and
      at least one entry without membership; verify with unit tests that the same id gives the same
      entries and that the result is sorted.
- [ ] 1.2 Add `withCurrentSession(entries, detail, clientId)` that inserts or replaces the entry for
      `detail.sessionId` with the participant's current status from `MockSessionDetail`; verify with
      unit tests for insert and replace.
- [ ] 1.3 Add `attendanceSummary(entries)` (`PRESENT`/`LATE` → attended, `ABSENT`/`SICK` → missed)
      and `filterByPeriod(entries, from, to)`; verify with unit tests including the spec scenario
      «Подсчёт сводки» and empty period.

## 2. History component

- [ ] 2.1 Create `web/src/clients/ClientAttendanceHistory.tsx`: summary + table (status icon with
      text label, date/time, group, coach, membership or «(не задан)», comment), empty state,
      optional `highlightSessionId` row highlight; verify with a render test.
- [ ] 2.2 Add i18n keys used by the history to `web/src/i18n/ru.ts` and `en.ts`.

## 3. Client visits page

- [ ] 3.1 Create `web/src/clients/ClientVisitsPage.tsx` (header, back to `/clients/$clientId`,
      period filter, `ClientAttendanceHistory`); wire it to `/clients/$clientId/visits` in
      `web/src/app/router.tsx` with zod-validated `from` / `to` search params instead of
      `ClientComingSoonPage`; verify with a page test for the period filter scenario.

## 4. Drill-in from session detail

- [ ] 4.1 Add zod-validated optional `client` search param to the `/sessions/$sessionId` route;
      invalid values are treated as absent; verify with a router test.
- [ ] 4.2 Create `web/src/sessions/ClientAttendanceSheet.tsx`: `Sheet` with participant name,
      «Открыть карточку» link, and `ClientAttendanceHistory` fed by
      `withCurrentSession(mockClientAttendance(id), detail, id)` with the current session highlighted.
- [ ] 4.3 Make participant names in `SessionDetailPage.tsx` buttons that set `?client=`; render the
      sheet when the param matches a participant; close removes the param; verify with page tests
      for opening, status change reflected in the sheet, and closing.

## 5. Checks

- [ ] 5.1 Run `npm run format` and `npm run check` in `web/`.
