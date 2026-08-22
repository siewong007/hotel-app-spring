#!/usr/bin/env python3
"""Extract always-on reference-data SQL from the Rust project's seed.sql."""
import re
from pathlib import Path

SEED = Path("/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-app-be/"
            "database/postgres/seed.sql")
OUT = Path("src/main/resources/db/reference-data.sql")

RANGES = [
    (374, 401),    # roles
    (402, 539),    # permissions
    (540, 634),    # role_permissions
    (635, 806),    # route_access_policies
    (807, 877),    # system_settings
    (878, 905),    # booking_channels
    (906, 943),    # loyalty programs/tiers/rules
    (1038, 1284),  # extra setting + backfill route policies
    (1573, 1610),  # july promotion + promotion_room_types + loyalty_rewards
]

EXCLUDE_PATTERNS = [
    r"expected_", r"v1_seed_state", r"RAISE", r"app\.invalid_data_quarantine",
    r"missing_seed_count", r"\bDO\b", r"setval", r"CREATE TEMP",
    r"hotel_schema_revisions", r"INSERT INTO users", r"INSERT INTO user_roles",
]


def split_statements(lines):
    statements = []
    buf = []
    depth = 0
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("--"):
            continue
        if re.match(r"^\\(echo|connect|quit)", stripped):
            continue
        buf.append(line)
        depth += line.count("(") - line.count(")")
        if depth == 0 and stripped.endswith(";"):
            statements.append("\n".join(buf).strip())
            buf = []
    return [s for s in statements if s]


def main():
    lines = SEED.read_text().split("\n")
    chunks = []
    for start, end in RANGES:
        chunks.extend(lines[start - 1:end])
    kept = []
    for statement in split_statements(chunks):
        first_line = statement.split("\n", 1)[0].strip()
        if not re.match(r"^(INSERT INTO|UPDATE |DELETE FROM)", statement):
            continue
        lowered = statement.lower()
        if any(re.search(p, lowered) for p in EXCLUDE_PATTERNS):
            continue
        kept.append(statement)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    text = "\n\n".join(kept) + "\n"
    assert len(kept) >= 20, f"only {len(kept)} statements kept"
    OUT.write_text(text)
    print(f"wrote {OUT}: {len(kept)} statements, {len(text.splitlines())} lines")


if __name__ == "__main__":
    main()
