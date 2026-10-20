# ENotes screen inventory

Planning inventory: 80 entries across nine groups. These entries include both destinations and reusable state patterns; they are not necessarily 80 separate navigation destinations. Dialogs and native Android/provider surfaces are listed separately.

## A. Launch, onboarding and access (SCR-001–008)
- SCR-001 Splash and startup routing
- SCR-002 Welcome and onboarding
- SCR-003 Onboarding preferences
- SCR-004 Notification permission explanation
- SCR-005 Notification permission result/recovery
- SCR-006 App-lock setup
- SCR-007 Unlock screen
- SCR-008 Privacy and AI data notice

## B. Home and navigation (SCR-009–015)
- SCR-009 Home dashboard
- SCR-010 Navigation hub
- SCR-011 Global search
- SCR-012 Search results
- SCR-013 Activity inbox
- SCR-014 Favorites and pinned items
- SCR-015 Recently opened

## C. Notes and organization (SCR-016–026)
- SCR-016 All notes
- SCR-017 Create note
- SCR-018 Edit existing note
- SCR-019 Note reading view
- SCR-020 Folders list
- SCR-021 Folder detail
- SCR-022 Tags and labels
- SCR-023 Bulk selection
- SCR-024 Trash
- SCR-025 Archive
- SCR-026 Note templates

## D. Documents and attachments (SCR-027–034)
- SCR-027 Documents library
- SCR-028 Document details
- SCR-029 In-app document viewer
- SCR-030 External viewer handoff
- SCR-031 Attachment picker/system picker handoff
- SCR-032 Import progress and result
- SCR-033 Text extraction/indexing status
- SCR-034 Storage manager

## E. Google Drive (SCR-035–042)
- SCR-035 Connect Google Drive
- SCR-036 Authorization handoff/result
- SCR-037 Drive browser
- SCR-038 Drive search results
- SCR-039 Drive file details
- SCR-040 Open Drive file
- SCR-041 Drive connection settings
- SCR-042 Drive errors and recovery

## F. AI assistant (SCR-043–052)
- SCR-043 AI landing page
- SCR-044 AI chat conversation
- SCR-045 AI answer details and sources
- SCR-046 AI document-result card
- SCR-047 AI file-search results
- SCR-048 AI action proposal review
- SCR-049 AI action confirmation
- SCR-050 AI action result
- SCR-051 AI settings and provider status
- SCR-052 AI unavailable/error state

## G. Reminders and calendar (SCR-053–059)
- SCR-053 Reminders list
- SCR-054 Create reminder
- SCR-055 Edit reminder
- SCR-056 Reminder detail
- SCR-057 Calendar
- SCR-058 Notification action handling
- SCR-059 Repeat schedule editor

## H. Account, sync, backup and settings (SCR-060–070)
- SCR-060 Settings hub
- SCR-061 Appearance settings
- SCR-062 Notes and editor settings
- SCR-063 Reminder settings
- SCR-064 Account and sign-in
- SCR-065 Sync status and conflicts
- SCR-066 Backup and restore
- SCR-067 Privacy and data controls
- SCR-068 Permissions and system settings
- SCR-069 About, help and diagnostics
- SCR-070 Account and data deletion

## I. Cross-cutting states and recovery (SCR-071–080)
- SCR-071 Offline mode
- SCR-072 Loading and skeleton states
- SCR-073 General error and retry
- SCR-074 Empty-state library
- SCR-075 Permission-denied recovery
- SCR-076 Unsaved changes and conflicts
- SCR-077 Destructive-action confirmation
- SCR-078 Multi-select toolbar
- SCR-079 Accessibility and responsive layouts
- SCR-080 App update and migration

## Reusable dialogs/bottom sheets (DIA-001–018)
- DIA-001 Create/rename folder
- DIA-002 Move item
- DIA-003 Delete/restore item
- DIA-004 Empty trash
- DIA-005 Quick reminder editor
- DIA-006 AI action confirmation
- DIA-007 AI permission explanation
- DIA-008 Drive connect/disconnect
- DIA-009 Import options
- DIA-010 Sort/filter sheet
- DIA-011 Document action menu
- DIA-012 Note action menu
- DIA-013 Sign-out confirmation
- DIA-014 Restore backup confirmation
- DIA-015 Clear storage confirmation
- DIA-016 Network/provider error
- DIA-017 Unsaved changes
- DIA-018 Permission rationale

## Native/system interactions (SYS-001–005)
- SYS-001 Android notification permission prompt
- SYS-002 Android document/file picker
- SYS-003 Google account authorization and consent
- SYS-004 Android biometric/system credential prompt
- SYS-005 Android share sheet/Open With chooser

## Flow infographic set
1. Navigation and app structure
2. Create/edit/autosave a note
3. Import and open a document
4. Connect and browse Google Drive
5. Ask AI and inspect sources
6. Review and confirm an AI action
7. Create and manage reminders
8. Account and sync/conflict resolution
9. Backup and restore
10. Security, permissions, and recovery

## Per-screen acceptance checklist
For each screen, document purpose, controls, tap outcomes, navigation, data source, loading/empty/error/offline states, permission needs, accessibility, destructive-action safeguards, and test cases. Native provider UI should be specified as a handoff rather than inaccurately recreated.
