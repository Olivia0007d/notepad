#!/usr/bin/env bash
#
# Decide whether a change touched a file that ships in the APK or affects how it
# is built. Used by the `guard` job in version-guard.yml and auto-release.yml, so
# the rule lives in one place. Writes code_changed=true|false to $GITHUB_OUTPUT.
#
# Reads its context from the environment: EVENT_NAME, REPOSITORY, PR, BASE_SHA,
# HEAD_SHA, BEFORE_SHA and AFTER_SHA.
set -euo pipefail

if [ "${EVENT_NAME}" = "pull_request" ]; then
  base="${BASE_SHA}"
  head="${HEAD_SHA}"
elif [ "${EVENT_NAME}" = "workflow_dispatch" ]; then
  base=$(gh api "repos/${REPOSITORY}/pulls/${PR}" --jq '.base.sha')
  head=$(gh api "repos/${REPOSITORY}/pulls/${PR}" --jq '.head.sha')
else
  base="${BEFORE_SHA}"
  head="${AFTER_SHA}"
fi

if [ -z "${base}" ] || [ "${base}" = "0000000000000000000000000000000000000000" ]; then
  base=$(git rev-parse "${head}^" 2>/dev/null || echo "")
fi

if [ -z "${base}" ]; then
  echo "No base commit to compare against; treating this as a change."
  echo "code_changed=true" >> "$GITHUB_OUTPUT"
  exit 0
fi

# A file that ships in the APK or affects how it is built.
is_shipping() {
  case "$1" in
    app/*) return 0 ;;
    *.gradle.kts|*.gradle|gradle.properties|settings.gradle.kts|gradle/libs.versions.toml|gradle/wrapper/*) return 0 ;;
  esac
  return 1
}

# The three-dot form compares against the merge base, so a pull request shows
# only its own changes even when main has moved on.
changed=$(git diff --name-only "${base}...${head}" || true)
echo "Changed files:"
echo "${changed}"
echo

substantive=""
while IFS= read -r file; do
  [ -z "${file}" ] && continue
  is_shipping "${file}" || continue

  if git diff --quiet -w --ignore-blank-lines "${base}...${head}" -- "${file}"; then
    echo "skip (whitespace/formatting only): ${file}"
    continue
  fi

  edits=$(git diff -w --ignore-blank-lines "${base}...${head}" -- "${file}" \
    | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' || true)
  code=$(printf '%s\n' "${edits}" | grep -vE '^[+-][[:space:]]*(//|/\*|\*|<!--|-->)' || true)
  if [ -z "${code}" ]; then
    echo "skip (comments only): ${file}"
    continue
  fi

  echo "counts as a change: ${file}"
  substantive="${substantive}${file}"$'\n'
done <<< "${changed}"

echo
if [ -n "${substantive}" ]; then
  echo "A shipping file changed; a version bump is required."
  echo "code_changed=true" >> "$GITHUB_OUTPUT"
else
  echo "No shipping file changed; nothing to release."
  echo "code_changed=false" >> "$GITHUB_OUTPUT"
fi
