#!/usr/bin/env python3
"""Generate exact Fabric API targets for every supported stable Minecraft release."""
import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
minecraft = json.loads((root / "src/minecraft-version-index.json").read_text(encoding="utf-8"))
if not isinstance(minecraft, list):
    raise SystemExit("Unexpected Minecraft version index format")

def supported(identifier):
    return bool(re.fullmatch(r"1\.(?:1[5-9]|2[01])(?:\.\d+)?|26\.\d+(?:\.\d+)?", identifier))

versions = []
for row in minecraft:
    if isinstance(row, list):
        identifier, kind = row[0], row[1]
    else:
        identifier, kind = row["id"], row["type"]
    if kind == "release" and supported(identifier):
        versions.append(identifier)

url = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
request = urllib.request.Request(url, headers={"User-Agent": "Quartz-Client-build/1.0"})
with urllib.request.urlopen(request, timeout=30) as response:
    metadata = ET.fromstring(response.read())
published = [item.text for item in metadata.findall("./versioning/versions/version") if item.text]
# Some family-suffixed Fabric API releases require a newer Minecraft patch.
exact_legacy_api = {
    "1.16": "0.13.1+build.370-1.16",
    "1.16.1": "0.13.1+build.370-1.16",
    "1.17": "0.36.0+1.17",
    "1.18": "0.44.0+1.18",
}
targets = []
for identifier in versions:
    parts = identifier.split(".")
    candidates = [identifier]
    if len(parts) == 3:
        candidates.append(".".join(parts[:2]))
    api = exact_legacy_api.get(identifier)
    if api is not None and api not in published:
        raise SystemExit(f"Fabric API {api} for Minecraft {identifier} is not published")
    for candidate in candidates if api is None else []:
        suffix = "+" + candidate
        api = next((version for version in reversed(published) if version.endswith(suffix)), None)
        if api is not None:
            break
    if api is None:
        raise SystemExit(f"No published Fabric API found for Minecraft {identifier}")
    project = "quartz-mod-26.3" if identifier.startswith("26.") else "quartz-mod"
    targets.append({"id": identifier, "project": project, "api": api, "port": True})
(root / "src/quartz-build-targets.json").write_text(json.dumps(targets, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(targets)} stable Fabric targets from 1.15.x through 26.x")
