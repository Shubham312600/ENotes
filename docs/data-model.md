# ENotes domain data model — proposal

This is a starting model for architecture review, not an implemented database schema. Final types and migrations depend on the selected stack.

## Note
- id: stable unique identifier
- title: optional string
- body: note content in a defined format
- createdAt, updatedAt
- folderId: optional reference
- tagIds: set of references
- isPinned, isArchived
- deletedAt: nullable trash marker
- localVersion / remoteVersion: optional sync metadata
- syncState: local-only, pending, synced, conflict, failed
- contentFormatVersion: migration support

## Folder
- id, name, createdAt, updatedAt
- optional parentId if nested folders are supported
- ordering metadata

## Tag
- id, name, createdAt
- optional color token (must not be the only indicator)

## Document
- id, displayName, mimeType, size when known
- source: local import, note attachment, or connected provider
- local URI/reference or provider file ID (never assume a remote file is locally cached)
- importedAt, lastOpenedAt, availability status
- preview/extraction capability status based on actual format support

## Reminder
- id, title, optional description
- scheduledAt, timezone policy
- recurrence rule and optional end condition
- linked note/document reference
- completion state and timestamps
- notification scheduling status

## Attachment
- id, parent note ID, display name, MIME type, size
- content storage reference and checksum where useful
- availability and import state

## Activity event
- id, type, timestamp
- related item ID where applicable
- user-safe summary
- avoid storing unnecessary sensitive content

## Sync conflict
- id, entity ID/type
- local and remote version references
- detectedAt and resolution status
- retain both versions until a safe resolution is confirmed

## Relationships and integrity
- Deleting a folder must define how its notes are handled before implementation.
- Deleting a note and permanently deleting a note are distinct states.
- A missing remote Drive file must not be confused with a locally stored file.
- Restoring backup must preview merge/overwrite effects.
- All timestamps and recurring reminders must define timezone semantics.

## Data-model acceptance checks
- Stable IDs and migration strategy exist.
- Referential integrity and deletion rules are tested.
- Search indexing can be rebuilt from authoritative stored content.
- Sync retries do not duplicate writes.
- Sensitive content is excluded from routine logs.
