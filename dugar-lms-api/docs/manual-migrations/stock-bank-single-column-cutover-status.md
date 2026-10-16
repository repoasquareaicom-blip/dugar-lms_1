# Final cutover attempt: blocked before conversion

The identified local Spring Boot application and its Maven parent were stopped. Ten remaining idle JDBC sessions were closed on the confirmed local development database. A fresh JDBC connection pool immediately reconnected, despite no local Java processes remaining. Its source could not be stopped from the available local process controls.

Both guarded cutover attempts stopped at the writer check, before acquiring table locks, taking a new cutover backup, or executing conversion SQL. No schema/data migration was executed and no source column was dropped. The previously prepared backup and rollback rehearsals remain available.

The local backend remains stopped. It has not been restarted because the updated code targets the converted schema, while the database remains on the old schema. Stop the other connected application/pool (and any manual bank-name edits), then repeat the guarded cutover with a fresh backup. Database integration tests for the final schema were not run because the conversion did not proceed; prior rollback-rehearsal tests passed.

See `stock-bank-single-column-cutover-blocked.json` for current column types and counts. No production or AWS action occurred.
