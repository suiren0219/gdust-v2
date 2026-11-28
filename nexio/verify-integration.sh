#!/usr/bin/env bash
# Read-only checks for the filtered source import; no Gradle or network access.
set -euo pipefail
integration_root="$(cd -- "$(dirname -- "$0")" && pwd)"
integration_python="${PYTHON:-python}"
"$integration_python" -I - "$integration_root" <<'PY'
import hashlib
import json
import pathlib
import re
import sqlite3
import sys

root = pathlib.Path(sys.argv[1]).resolve()
manifest = json.loads((root / "integration-import.json").read_text(encoding="utf-8"))
expected = {
    "nexio": "cfdaedbae2359e9249d1415e1e5d963c66c66cf1",
    "softglass": "34011b51282307db77f8153b7ef1ec597cd284c9",
    "nexioui": "499fc1e318cd5fc82aa5709b9ef42655b547d549",
    "gdust-compat": "68a638da6dc69f06be3782c0835b116a69a87832",
}
for name, commit in expected.items():
    assert manifest["sources"][name]["commit"] == commit, name
for rel in ["LICENSE", "third_party/softglass/LICENSE", "third_party/nexioui/LICENSE",
            "third_party/nexioui/NOTICE", "adapters/gdust-compat/LICENSE"]:
    assert (root / rel).is_file(), rel

settings = (root / "settings.gradle.kts").read_text(encoding="utf-8")
assert 'file("third_party/softglass")' in settings
assert 'file("third_party/nexioui")' in settings
assert 'file("../softglass")' not in settings and 'file("../nexioui")' not in settings
vendor_settings = (root / "third_party/nexioui/settings.gradle.kts").read_text(encoding="utf-8")
assert 'file("../softglass")' in vendor_settings
assert 'require(softglassSource.resolve("build.gradle.kts").isFile)' in vendor_settings
assert (root / "third_party/nexioui/../softglass").resolve().is_relative_to(root)
oh = (root / "ohos/sharedBackdrop/build.gradle.kts").read_text(encoding="utf-8")
assert '../third_party/softglass/src/commonMain/kotlin' in oh

profile = json.loads((root / "harmonyApp/build-profile.json5").read_text(encoding="utf-8"))
assert profile["app"].get("signingConfigs") == []
assert all("signingConfig" not in product for product in profile["app"]["products"])
credential = re.compile(r'''(?i)["']?(?:storePassword|keyPassword)["']?\s*[:=]\s*["'][^"'\n]+["']''')
private_prefixes = ["/" + "Users" + "/", "/" + "home" + "/"]
windows_home = re.compile(re.escape(":" + "/" + "Users" + "/"), re.IGNORECASE)
fixture_files = {"ohos/third_party/sqlite/sqlite3.c", "ohos/third_party/sqlite/sqlite3.h"}
for path in root.rglob("*"):
    if not path.is_file():
        continue
    rel = path.relative_to(root).as_posix()
    assert path.suffix.lower() not in {".apk", ".aab", ".ipa", ".p12", ".jks", ".keystore", ".p7b", ".a", ".so"}, rel
    try:
        text = path.read_bytes().decode("utf-8")
    except UnicodeError:
        continue
    # SQLite upstream documents use illustrative /home/... paths; retain vendor source verbatim.
    if rel not in fixture_files and path.name != "verify-integration.sh":
        assert not any(prefix in text for prefix in private_prefixes), f"Private home path: {rel}"
        assert not windows_home.search(text.replace(chr(92), "/")), f"Private Windows home path: {rel}"
    if path.name != "verify-integration.sh":
        assert not credential.search(text), f"Literal signing password: {rel}"
    if path.name == "gradle-wrapper.properties":
        assert "\x00" not in text, rel

with sqlite3.connect(":memory:") as db:
    db.deserialize((root / "core/src/commonMain/sqldelight/databases/1.db").read_bytes())
    tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")]
    assert tables
    assert all(db.execute('SELECT count(*) FROM "' + table + '"').fetchone()[0] == 0 for table in tables)

for rel in ["androidApp/build", "core/build", "desktopApp/build", "ohos/third_party/sqlite/lib",
            "harmonyApp/entry/src/main/resources/rawfile/composeResources"]:
    candidate = root / rel
    assert not candidate.exists(), f"Generated directory: {rel}"

# Each retained vendor binary is an upstream build dependency, not an application output.
for rel, digest in manifest.get("retained_binary_sha256", {}).items():
    assert hashlib.sha256((root / rel).read_bytes()).hexdigest() == digest, rel
files = sum(path.is_file() for path in root.rglob("*"))
assert files == manifest["final_file_count"], (files, manifest["final_file_count"])
print(f"Static integration checks passed: {files} files; {len(tables)} empty schema tables")
PY
