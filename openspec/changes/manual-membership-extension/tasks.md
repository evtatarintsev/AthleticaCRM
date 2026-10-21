## 1. Server contract and domain

- [ ] 1.1 Add the extension request schema and API route; verify the generated contract includes membership ID, target end date, and reason.
- [ ] 1.2 Implement the domain operation for active memberships, reject target dates not after the current end date, and verify state/date validation with focused tests.
- [ ] 1.3 Record old date, new date, and reason through `AuditMemberships` in the same transaction; verify the audit entry includes the acting employee and all extension details.

## 2. Web client

- [ ] 2.1 Add the extension form to the membership panel for active memberships; verify it is absent for inactive, closed, and revoked memberships.
- [ ] 2.2 Submit the target date and reason, update the displayed membership on success, and show server errors without closing the panel; verify with focused UI tests.
- [ ] 2.3 Add Russian and English labels and validation messages; verify both locales render the form and errors.

## 3. Integration

- [ ] 3.1 Run the relevant server and web checks and verify successful extension updates the end date and audit while preserving other membership terms.
