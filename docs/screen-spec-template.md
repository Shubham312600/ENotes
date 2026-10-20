# Screen-level specification template

Copy this template for every screen that needs implementation-level detail.

## Identity
- Screen ID:
- Name:
- Phase:
- Priority: P0 / P1 / P2
- Status: Planned / In progress / Implemented / Verified
- Entry points:
- Exit/navigation destinations:

## Purpose and user stories
- User goal:
- Primary user story:
- Secondary user stories:

## Layout
- App bar:
- Main content:
- Primary action:
- Secondary actions:
- Bottom navigation or contextual navigation:
- Light theme:
- Dark theme:
- Large-font / narrow-screen behavior:

## Components and interactions
| Component/control | Default state | User action | Result/navigation | Validation/error |
|---|---|---|---|---|
| Example control | Idle | Tap | Opens target | Explain failure |

## Data and persistence
- Entities/fields read:
- Entities/fields written:
- Local persistence:
- Remote service/backend:
- Cache/offline behavior:
- Data ownership and deletion behavior:

## States
- Initial/loading:
- Empty:
- Success:
- Error/retry:
- Offline:
- Permission denied:
- Unsaved changes/conflict:
- Disabled/unsupported:

## Security and privacy
- Permissions/scopes:
- Sensitive data displayed:
- Confirmation required:
- Secret handling:
- Logging/diagnostics constraints:

## Accessibility
- Accessible labels:
- Focus order:
- Touch targets:
- Contrast and non-color status cues:
- Screen-reader announcements:
- Font scaling:

## Acceptance criteria
- [ ] Primary flow works.
- [ ] Back navigation works.
- [ ] Data persists or is intentionally transient.
- [ ] Loading/empty/error states are correct.
- [ ] Destructive actions are confirmed.
- [ ] Accessibility reviewed.
- [ ] Tests added and passed.

## Test cases
1. Happy path:
2. Invalid input:
3. Offline/provider failure:
4. Permission denied:
5. Process/app restart:
6. Accessibility/theme variant:

## Visual assets
- Reference image path:
- Annotated infographic path:
- Notes on any native Android/provider UI:
