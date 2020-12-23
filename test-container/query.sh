#!/bin/sh

export PGPASSWORD=$DB_PASS

psql -h /cloudsql/$CLOUD_SQL_CONNECTION_NAME -U postgres -c "SELECT 1"
