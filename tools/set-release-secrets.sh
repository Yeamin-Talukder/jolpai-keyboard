#!/usr/bin/env bash
# Saves the release signing key as GitHub Actions secrets, so the Release workflow
# (.github/workflows/release.yml) can build store-signed APKs when a v* tag is pushed.
#
# Run it yourself, once, from the project folder in Git Bash:
#     bash tools/set-release-secrets.sh
#
# It reads keystore.properties (git-ignored) for the key file, alias and passwords, and sends
# them to GitHub with the GitHub CLI (`gh`, logged in to an account that can edit the repo).
# Nothing is printed: values go to `gh` through stdin, never onto the screen or into the shell
# history. GitHub stores secrets encrypted; workflows can use them but nobody can read them back.
set -euo pipefail

REPO="Yeamin-Talukder/jolpai-keyboard"
cd "$(dirname "$0")/.."
PROPS="keystore.properties"
[ -f "$PROPS" ] || { echo "keystore.properties not found in $(pwd)"; exit 1; }

prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r'; }
STORE_FILE="$(prop storeFile)"
# storeFile may be relative to the project folder or absolute (a Windows path works too).
[ -f "$STORE_FILE" ] || STORE_FILE="$(cygpath -u "$STORE_FILE" 2>/dev/null || echo "$STORE_FILE")"
[ -f "$STORE_FILE" ] || { echo "Key file not found: $(prop storeFile)"; exit 1; }

for name in storePassword keyAlias keyPassword; do
    [ -n "$(prop $name)" ] || { echo "$name is missing in keystore.properties"; exit 1; }
done

echo "Saving the signing key to GitHub secrets for $REPO …"
# printf '%s' sends each value exactly, without a trailing line break.
printf '%s' "$(base64 -w0 "$STORE_FILE")" | gh secret set KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$(prop storePassword)" | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$(prop keyAlias)" | gh secret set KEY_ALIAS --repo "$REPO"
printf '%s' "$(prop keyPassword)" | gh secret set KEY_PASSWORD --repo "$REPO"

echo
echo "Done. Secrets now on GitHub (names only):"
gh secret list --repo "$REPO"
