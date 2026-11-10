## 1. Audit read projection

- [x] 1.1 Add `ActivityLogView`/`DbActivityLogView`, register it in `ReadViews`, and verify organization scoping, filters, ordering, page results, and `total` with database tests.
- [x] 1.2 Keep `AuditLog` and `PostgresAuditLog` write-only by moving list/count reads to the projection; verify existing audit-write tests and route wiring.

## 2. API response and localized descriptions

- [x] 2.1 Extend shared audit response schemas with a human-readable description and typed subject `{ type, id, title, isAvailable, targetId? }`; regenerate web contracts and verify the generated types include the new fields.
- [x] 2.2 Resolve current subject titles for supported entity types in the audit read query and map action/entity combinations to localized server messages; verify all `AuditActionType` values, null subjects, deleted subjects, and unknown entity types.
- [x] 2.3 Route `GET /audit/log` through `ActivityLogView` and return the localized description and subject; verify pagination and filters are passed through and existing API callers compile.
- [x] 2.4 Add `GET /audit/log/users` with distinct actor user IDs and their saved names scoped to the current organization; verify actors from another organization are excluded.

## 3. Web activity log

- [x] 3.1 Render the description and subject title in the activity log, linking only supported subject types to their existing application routes; verify unsupported and missing subjects have no link and do not display UUID as the title.
- [x] 3.2 Add period, employee, and action filters to the activity log and pass their values to the API; verify each filter combination updates results and resets pagination.
- [x] 3.3 Add Russian and English strings for action descriptions, subjects, filters, and empty states; verify localization keys exist in both dictionaries.

## 4. Integration verification

- [x] 4.1 Add or update DB and web tests for readable descriptions, subject routing/fallback, organization isolation, filters, and pagination; verify the focused suites pass.
- [x] 4.2 Run server build/format and web contract/check commands; verify all complete successfully.
