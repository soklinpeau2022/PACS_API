# Database Migrations

Flyway migrations in `src/main/resources/db/migration` are the only canonical
schema migrations for this project.

Do not copy or apply individual migration files with `psql`, because that would
bypass `flyway_schema_history`. Use:

```bash
bash scripts/stack.sh local db-migrate --build
```

or:

```powershell
.\scripts\stack.ps1 -Target local -Action db-migrate -Build
```

The command creates a Docker backup first, deploys the API so Flyway applies
pending migrations, and then runs the consolidated database validation.
