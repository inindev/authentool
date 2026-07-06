#!/usr/bin/env bash
#
# Interop proof: demonstrates that the macOS AuthentoolCore is byte-for-byte
# compatible with the Android reference CLI tools, in BOTH directions, for both the crypto
# blob (cli/crypt.py) and TOTP generation (cli/totp_gen.py).
#
# Requirements:
#   - Xcode (set DEVELOPER_DIR or have it selected).
#   - A Python with the `cryptography` package, passed via $PYTHON (defaults to python3).
#   - The Android repo, passed via $ANDROID_REPO (defaults to ../authentool_android).
#
# Usage:
#   PYTHON=/path/to/venv/bin/python ./scripts/interop_check.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_DIR="$REPO_ROOT/AuthentoolCore"
ANDROID_REPO="${ANDROID_REPO:-$REPO_ROOT/../authentool_android}"
PYTHON="${PYTHON:-python3}"
CRYPT_PY="$ANDROID_REPO/cli/crypt.py"
TOTP_PY="$ANDROID_REPO/cli/totp_gen/totp_gen.py"

pass=0
fail=0
check() { # check <label> <expected> <actual>
    if [[ "$2" == "$3" ]]; then
        echo "  PASS  $1"
        pass=$((pass + 1))
    else
        echo "  FAIL  $1"
        echo "        expected: $2"
        echo "        actual:   $3"
        fail=$((fail + 1))
    fi
}

echo "Building interop-cli..."
( cd "$CORE_DIR" && swift build --product interop-cli >/dev/null )
BIN="$CORE_DIR/.build/debug/interop-cli"

PLAINTEXT='[{"name":"GitHub","seed":"MZXW6YTBOJXXEABC"},{"name":"Example","seed":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"}]'
PASSWORD='correct horse battery'

echo
echo "[1] Crypto: Swift encrypt -> Python decrypt"
swift_blob="$(printf '%s' "$PLAINTEXT" | "$BIN" encrypt "$PASSWORD")"
py_decrypted="$("$PYTHON" - "$CRYPT_PY" "$PASSWORD" "$swift_blob" <<'PY'
import importlib.util, sys
path, password, blob = sys.argv[1], sys.argv[2], sys.argv[3]
spec = importlib.util.spec_from_file_location("crypt", path)
c = importlib.util.module_from_spec(spec); spec.loader.exec_module(c)
sys.stdout.write(c.decrypt(blob, password))
PY
)"
check "python decrypts swift blob" "$PLAINTEXT" "$py_decrypted"

echo
echo "[2] Crypto: Python encrypt -> Swift decrypt"
py_blob="$("$PYTHON" - "$CRYPT_PY" "$PASSWORD" "$PLAINTEXT" <<'PY'
import importlib.util, sys
path, password, plaintext = sys.argv[1], sys.argv[2], sys.argv[3]
spec = importlib.util.spec_from_file_location("crypt", path)
c = importlib.util.module_from_spec(spec); spec.loader.exec_module(c)
sys.stdout.write(c.encrypt(plaintext, password))
PY
)"
swift_decrypted="$(printf '%s' "$py_blob" | "$BIN" decrypt "$PASSWORD")"
check "swift decrypts python blob" "$PLAINTEXT" "$swift_decrypted"

echo
echo "[3] TOTP: Swift generator == Python generator (same seed, same time)"
# Note: totp_gen.py does `timestamp = timestamp or time.time()`, so it cannot be asked for
# t=0 (0.0 is falsy and falls back to now). We compare over realistic non-zero timestamps;
# the t=0 case is covered by the Swift unit-test golden vectors instead.
for pair in "MZXW6YTBOJXXEABC:59" "MZXW6YTBOJXXEABC:1234567890" \
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ:1700000000" "JBSWY3DPEHPK3PXP:2000000000"; do
    seed="${pair%%:*}"
    t="${pair##*:}"
    swift_code="$("$BIN" totp "$seed" "$t")"
    py_code="$("$PYTHON" - "$TOTP_PY" "$seed" "$t" <<'PY'
import importlib.util, sys
path, seed, t = sys.argv[1], sys.argv[2], float(sys.argv[3])
spec = importlib.util.spec_from_file_location("totp", path)
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
sys.stdout.write(m.generate_totp(m.base32_decode(seed), t))
PY
)"
    check "totp $seed @ $t" "$py_code" "$swift_code"
done

echo
echo "Interop summary: $pass passed, $fail failed"
[[ "$fail" -eq 0 ]]
