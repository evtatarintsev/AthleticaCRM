## 1. Server: group roster projection

- [ ] 1.1 Add to `shared` (`api/schemas/groups/`): `GroupClientAttention` enum (`DEBT`,
      `NO_MEMBERSHIP`, `MEMBERSHIP_ENDING`), `GroupRosterMembership` (`id`, `name`,
      `sessionsTotal?`, `sessionsRemaining?`, `endDate?`, `endingSoon`, `closePending`),
      `GroupRosterClient` (`id`, `name`, `avatarId?`, `membership?`, `unpaidSessions`, `balance`,
      `attention?`), `GroupRosterRequest` (`groupId`), `GroupRosterResponse` (`clients`).
- [ ] 1.2 Create `read/groups/GroupRosterView` + `DbGroupRosterView`: one SQL over active
      enrollments scoped by `ctx.orgId` and the group branch; membership acting in the group via
      `membership_groups` (active before not-started, then nearest end date; closed and revoked
      excluded); `endingSoon` / `closePending` derived the same way as `membership-lifecycle`;
      unpaid sessions per `membership-debits`; balance per `client-balance`; attention rule and
      ordering per `group-roster` spec.
- [ ] 1.3 DB test `server/src/test/kotlin/org/athletica/crm/read/groups/GroupRosterViewTest.kt`
      covering every scenario of `specs/group-roster/spec.md`.
- [ ] 1.4 Register in `ReadViews`; add GET `groups/roster` to `routes/GroupsRoutes.kt`.
- [ ] 1.5 `./gradlew build`, `./gradlew ktlintFormat`, `cd web && npm run contracts`, commit
      `web/src/api/generated/`.

## 2. Web: data and pure logic

- [ ] 2.1 Add `useGroupRoster` and `useGroupAttendance` to `web/src/groups/groupsQueries.ts`.
- [ ] 2.2 `web/src/groups/groupOverview.ts`: attention counts by reason, roster filtering and
      search, membership grouping, attendance rate over the last 10 closed sessions, schedule slot
      merging for phone; unit tests for each spec scenario.

## 3. Web: page and tabs

- [ ] 3.1 zod-validated `tab` search param on `/groups/$groupId` in `web/src/app/router.tsx`
      (invalid → `overview`); router test.
- [ ] 3.2 Rewrite `GroupDetailPage.tsx`: header with discipline chips, «Добавить клиентов»,
      «⋯» menu (rename, schedule, disciplines, trainers — existing sheets/pickers); tab strip with
      attention indicator.
- [ ] 3.3 `GroupOverviewTab.tsx`: next session, attention, clients, attendance tiles; schedule and
      trainers cards; empty states.
- [ ] 3.4 `GroupClientsTab.tsx`: roster rows with membership, balance, reason and action (repay
      debt via the client membership sheet, issue membership sheet); search, filters with counts;
      keep add/remove behaviour from `group-clients`.
- [ ] 3.5 `GroupMembershipsTab.tsx`; move `GroupSessionsSection` into the «Занятия» tab;
      `GroupJournalTab.tsx` over `attendance/group` with period switching and links to session
      journals.
- [ ] 3.6 Responsive layout per design.md; i18n keys in `ru.ts` / `en.ts` with plural forms.
- [ ] 3.7 Update `GroupDetailPage.test.tsx` and add tab tests for the spec scenarios.

## 4. Checks

- [ ] 4.1 `npm run format` and `npm run check` in `web/`; `./gradlew build` and
      `./gradlew ktlintFormat` at the root.
