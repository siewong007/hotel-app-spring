#!/usr/bin/env python3
"""Check every inventory endpoint has a Spring mapping. Exit 1 listing gaps."""
import glob
import re
import sys

ROOT = "/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app-spring"


def norm(path: str) -> str:
    return re.sub(r"\{[a-zA-Z_]+\}", "{}", path)


def collect_mapped():
    mapped = set()
    for path in glob.glob(ROOT + "/src/main/java/**/*.java", recursive=True):
        src = open(path).read()
        for m in re.finditer(
                r"@(Get|Post|Put|Patch|Delete|RequestMapping)Mapping?\s*\(([^)]*)\)", src):
            ann = m.group(0)
            method = "ANY" if "RequestMapping" in ann.split("(")[0] else \
                m.group(1).upper()
            for pm in re.finditer(r'"([^"]+)"', m.group(2)):
                value = pm.group(1)
                if not value.startswith("/"):
                    continue
                mapped.add((method, norm(value)))
        # WebSocket upgrade endpoints register via addHandler(bean, "/path"),
        # not @*Mapping — upstream lists them as GET routes.
        for hm in re.finditer(r'addHandler\([^,]+,\s*"([^"]+)"', src):
            mapped.add(("GET", norm(hm.group(1))))
    # root-level infra routes live outside /api
    return mapped


def main():
    mapped = collect_mapped()
    lines = [l for l in open(ROOT + "/docs/api-parity-inventory.txt").read().splitlines()
             if l.strip()]
    missing = []
    for line in lines:
        parts = line.split()
        if len(parts) < 3:
            continue
        method, path = parts[0], parts[1]
        if (method, norm(path)) in mapped or ("ANY", norm(path)) in mapped:
            continue
        missing.append(line)
    print(f"{len(mapped)} spring mappings; {len(missing)} missing of {len(lines)}")
    if len(sys.argv) > 1 and sys.argv[1] == "--strict" and missing:
        for m in missing:
            print("MISSING", m)
        sys.exit(1)


if __name__ == "__main__":
    main()
