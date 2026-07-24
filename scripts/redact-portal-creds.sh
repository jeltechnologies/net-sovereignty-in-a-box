#!/bin/sh
# git clean filter for docker-compose.yaml (see .gitattributes): blanks the
# portal Basic Auth credentials and DOMAIN so real values never land in a
# commit, while leaving the working tree copy untouched. Configure with:
#   git config filter.portal-creds.clean scripts/redact-portal-creds.sh
#   git config filter.portal-creds.smudge cat
sed -E \
  -e 's/^([[:space:]]*PORTAL_USER_NAME:).*/\1 ""/' \
  -e 's/^([[:space:]]*PORTAL_PASSWORD:).*/\1 ""/' \
  -e 's/^([[:space:]]*DOMAIN:).*/\1 ""/'
