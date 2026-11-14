## 1. Server: group roster projection

- [x] 1.1 Move `ActiveStatus`, `Remaining` and `ActiveStatus.of` from
      `domain/memberships/MembershipRules.kt` to `shared` (`core/subscription/`) as pure code;
      `domain/memberships` keeps using them; existing membership tests stay green.
- [x] 1.2 Add to `shared` (`api/schemas/groups/`): `GroupClientAttention` enum (`DEBT`,
      `NO_MEMBERSHIP`, `MEMBERSHIP_ENDING`), `GroupRosterMembership` (`id`, `name`, `activated`,
      `sessionsTotal?`, `sessionsRemaining?`, `endDate?`, `endingSoon`, `closePending`),
      `GroupRosterClient` (`id`, `name`, `avatarId?`, `membership?`, `hasNextMembership`,
      `unpaidSessions`, `balance`, `attention?`), `GroupRosterRequest` (`groupId`),
      `GroupRosterResponse` (`clients`).
- [x] 1.3 Create `read/groups/GroupRosterView` + `DbGroupRosterView`: one SQL over active
      enrollments scoped by `ctx.orgId` and the group branch, returning raw fields; membership
      acting in the group via `membership_groups` picked by tiers (active not close-pending →
      inactive → active close-pending; then nearest end date `NULLS LAST`, then issued earlier;
      closed and revoked excluded); `hasNextMembership`; unpaid sessions per `membership-debits`;
      balance per `client-balance`. `endingSoon` / `closePending` via shared `ActiveStatus.of`,
      attention rule and ordering in Kotlin on the org's today, per `group-roster` spec.
- [x] 1.4 DB test `server/src/test/kotlin/org/athletica/crm/read/groups/GroupRosterViewTest.kt`
      covering every scenario of `specs/group-roster/spec.md`.
- [x] 1.5 Register in `ReadViews`; add GET `groups/roster` to `routes/GroupsRoutes.kt`.
- [x] 1.6 `./gradlew build`, `./gradlew ktlintFormat`, `cd web && npm run contracts`, commit
      `web/src/api/generated/`.

## 2. Web: data and pure logic

- [x] 2.1 Add `useGroupRoster` and `useGroupAttendance` to `web/src/groups/groupsQueries.ts`.
- [x] 2.2 `web/src/groups/groupOverview.ts`: attention counts by reason, roster filtering and
      search, membership grouping, attendance rate over the last 10 closed sessions, journal rows
      (current roster + marked former members), debt action choice (repay vs open client),
      schedule slot merging for phone; unit tests for each spec scenario.

## 3. Web: page and tabs

- [x] 3.1 zod-validated `tab` search param on `/groups/$groupId` in `web/src/app/router.tsx`
      (invalid → `overview`); router test.
- [x] 3.2 Rewrite `GroupDetailPage.tsx`: header with discipline chips, «Добавить клиентов»,
      «⋯» menu (rename, schedule, disciplines, trainers — existing sheets/pickers); tab strip with
      attention indicator.
- [x] 3.3 `GroupOverviewTab.tsx`: next session, attention, clients, attendance tiles; schedule and
      trainers cards; empty states.
- [x] 3.4 `GroupClientsTab.tsx`: roster rows with membership, balance, reason and action («Погасить
      долг» via the client membership sheet or «Открыть клиента»; «Оформить абонемент» /
      «Новый абонемент» via the issue membership sheet); search, filters with counts;
      keep add/remove behaviour from `group-clients`.
- [x] 3.5 `GroupMembershipsTab.tsx`; move `GroupSessionsSection` into the «Занятия» tab;
      `GroupJournalTab.tsx` over `attendance/group` with period switching and links to session
      journals.
- [x] 3.6 Responsive layout per design.md; i18n keys in `ru.ts` / `en.ts` with plural forms.
- [x] 3.7 Update `GroupDetailPage.test.tsx` and add tab tests for the spec scenarios.

## 4. Checks

- [x] 4.1 `npm run format` and `npm run check` in `web/`; `./gradlew build` and
      `./gradlew ktlintFormat` at the root.
