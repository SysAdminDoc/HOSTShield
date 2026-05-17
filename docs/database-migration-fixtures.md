# Database Migration Fixtures

Last refreshed: 2026-05-17

HostShield now keeps an instrumented migration matrix in `HostShieldMigrationTest`.
The test creates frozen SQL fixtures for every supported starting schema version
from 1 through 14, runs `Migrations.ALL`, and validates the result against the
current Room schema export for version 15. This extends the roadmap wording
from `v1->v14` to `v1..v14->v15` because v15 is the active source schema.

## Evidence Used

- `app/app/src/main/java/com/hostshield/data/database/Migrations.kt` - canonical migration chain.
- `app/app/src/main/java/com/hostshield/data/database/HostShieldDatabase.kt` - current database version and entities.
- `app/app/schemas/com.hostshield.data.database.HostShieldDatabase/` - tracked Room schema JSON for versions 7, 8, 9, 12, 14, and 15.
- Git tags `v1.0.0`, `v3.0.0`, `v3.6.0`, `v5.0.0`, `v6.2.0`, and `v6.3.0` - historical entity snapshots for versions 3, 6, 7, 9, 12, and 14.

## Known Gaps

Official Room schema JSONs for versions 1 through 6, 10, 11, and 13 were not
tracked in this repository. Versions 1 and 2 also predate the oldest tagged
Room snapshot found in Git; tag `v1.0.0` already uses database version 3.

Those missing official exports cannot be reconstructed byte-for-byte from the
current repository. The migration test therefore uses explicit SQL fixtures
derived from the migration chain and the closest tagged entity snapshots. The
fixtures are intentionally small and seed one row per table so the test checks
both final schema validity and data preservation across the upgrade path.
