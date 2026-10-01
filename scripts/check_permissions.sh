#!/usr/bin/env bash
# Fails if the packaged release APK declares any permission outside the allow-list.
# Usage: scripts/check_permissions.sh path/to/app-release.apk [merged-manifest.xml]
set -euo pipefail

APK="$1"
MERGED="${2:-}"
BUILD_TOOLS="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools"
AAPT2="$(ls -d "$BUILD_TOOLS"/*/ | sort -V | tail -1)aapt2"

# androidx.core adds an app-private, signature-level permission used only to protect
# runtime-registered receivers on older Android versions. It grants no capability.
ALLOWED_REGEX='^(com\.timestablequest\.app(\.debug)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$'

echo "== Permissions in packaged APK ($APK)"
PERMS="$("$AAPT2" dump permissions "$APK" | sed -n "s/.*uses-permission.*name='\([^']*\)'.*/\1/p; s/^uses-permission: name='\([^']*\)'.*/\1/p" | sort -u)"
echo "${PERMS:-<none>}"

FAIL=0
for p in $PERMS; do
  if ! [[ "$p" =~ $ALLOWED_REGEX ]]; then
    echo "::error::Unexpected permission: $p"
    FAIL=1
  fi
done

for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
  if echo "$PERMS" | grep -qx "$forbidden"; then
    echo "::error::Forbidden permission present: $forbidden"
    FAIL=1
  fi
done

if [[ -n "$MERGED" && -f "$MERGED" ]]; then
  echo "== uses-permission in merged manifest ($MERGED)"
  grep -o 'uses-permission[^>]*android:name="[^"]*"' "$MERGED" || echo "<none>"
fi

if [[ $FAIL -ne 0 ]]; then
  echo "Permission check FAILED"
  exit 1
fi
echo "Permission check passed"
