#!/usr/bin/env python3
"""Generate JPA entities + IdClasses from the hotel-app v1 baseline SQL schema."""
import re
import sys
from pathlib import Path

SQL_PATH = Path("/Volumes/APPLE EXTERNAL SSD /Personal Projects/hotel-app/hotel-app-be/"
                "database/postgres/migrations/0001_v1_baseline.sql")
OUT_DIR = Path("src/main/java/com/hotelapp/core/entity")

TYPE_MAP = [
    (r"^bigint$", "Long"),
    (r"^(integer|int)$", "Integer"),
    (r"^smallint$", "Short"),
    (r"^boolean$", "Boolean"),
    (r"^(character varying|varchar)\s*\(", "String"),
    (r"^text$", "String"),
    (r"^(numeric|money)", "java.math.BigDecimal"),
    (r"^(timestamp with time zone|timestamptz)$", "java.time.OffsetDateTime"),
    (r"^timestamp( without time zone)?$", "java.time.LocalDateTime"),
    (r"^date$", "java.time.LocalDate"),
    (r"^(time|time without time zone)$", "java.time.LocalTime"),
    (r"^uuid$", "java.util.UUID"),
    (r"^(jsonb|json|inet|cidr|macaddr|xml)$", "String"),
    (r"^real$", "Float"),
    (r"^double precision$", "Double"),
]


def map_type(sql_type: str) -> str:
    t = re.sub(r"\s+", " ", sql_type.strip().lower())
    if "[" in t:
        return "String[]"
    for pattern, java_type in TYPE_MAP:
        if re.match(pattern, t):
            return java_type
    return "String"



def normalize_type(t: str) -> str:
    t = re.sub(r"\s+", " ", t.strip().lower())
    if t.startswith("character varying"):
        m = re.match(r"character varying\s*\((\d+)\)", t)
        return f"varchar({m.group(1)})" if m else "varchar(255)"
    if t == "timestamp with time zone" or t == "timestamptz":
        return "timestamptz"
    if t.startswith("numeric"):
        return t.replace(" ", "")
    return t

def pascal(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("_"))


def parse_tables(sql: str):
    tables = {}
    pattern = (r"CREATE TABLE public\.([a-z_0-9]+) \((.*?)\n\)"
               r"(\s*PARTITION BY[^;]*;|;)")
    for match in re.finditer(pattern, sql, re.S):
        table_name = match.group(1)
        if table_name == "audit_logs_default":
            continue
        body = match.group(2)
        depth = 0
        parts, current = [], []
        for ch in body:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            if ch == "," and depth == 0:
                parts.append("".join(current))
                current = []
            else:
                current.append(ch)
        parts.append("".join(current))

        columns = []
        pk_column = None
        for part in parts:
            line = part.strip()
            if not line or re.match(
                    r"^(CONSTRAINT\s|PRIMARY KEY\s|UNIQUE\s|CHECK\s|FOREIGN KEY\s)",
                    line.upper()):
                continue
            col_match = re.match(r"^([a-z_][a-z_0-9]*)\s+(.+)$", line, re.S | re.I)
            if not col_match:
                continue
            col_name = col_match.group(1)
            rest = re.sub(r"\s+", " ", col_match.group(2)).strip()
            is_pk = bool(re.search(r"\bPRIMARY KEY\b", rest.upper()))
            type_part = re.split(r"\bPRIMARY KEY\b", rest, flags=re.I)[0]
            type_part = re.sub(r"\bCONSTRAINT\s+\S+", "", type_part, flags=re.I)
            type_part = re.sub(r"\bNOT NULL\b.*$", "", type_part, flags=re.I).strip()
            type_part = re.sub(r"\bDEFAULT\b.*$", "", type_part, flags=re.I).strip()
            type_part = re.sub(r"\bREFERENCES\b.*$", "", type_part, flags=re.I).strip()
            type_part = re.sub(r"\bUNIQUE\b.*$", "", type_part, flags=re.I).strip()
            if not col_name or not type_part:
                continue
            gen_match = re.search(r"\bGENERATED\b.*$", rest, flags=re.I)
            if gen_match:
                base_type_part = rest[:gen_match.start()].strip()
                raw_def = re.sub(r"\s+", " ",
                        (base_type_part + " " + gen_match.group(0)).strip())
            else:
                raw_def = normalize_type(type_part)
            columns.append((col_name, map_type(type_part), raw_def))
            if is_pk:
                pk_column = col_name
        if not columns:
            continue
        if pk_column is None:
            names = [c[0] for c in columns]
            pk_column = "id" if "id" in names else columns[0][0]
        tables[table_name] = (columns, pk_column)
    return tables


def parse_constraints(sql: str):
    uniques, composite_pks = {}, {}
    pattern = (r"ALTER TABLE ONLY public\.([a-z_0-9]+)\s+ADD CONSTRAINT [a-z_0-9]+ "
               r"(UNIQUE|PRIMARY KEY) \(([^)]+)\)\s*;")
    for match in re.finditer(pattern, sql):
        kind, cols_raw = match.group(2), match.group(3)
        cols = [c.strip().strip('"') for c in cols_raw.split(",")]
        if kind == "PRIMARY KEY":
            if len(cols) > 1:
                composite_pks[match.group(1)] = cols
            continue
        uniques.setdefault(match.group(1), set()).add(tuple(cols))
    return uniques, composite_pks


ENTITY_TEMPLATE = """package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
{generated_import}import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "{table}"{unique_block}){id_class_line}
public class {class_name}Entity {{

{fields}

{accessors}
}}
"""

ID_CLASS_TEMPLATE = """package com.hotelapp.core.entity;

import java.io.Serializable;
import java.util.Objects;

public class {class_name}Id implements Serializable {{

{fields}

{accessors}
    @Override
    public boolean equals(Object o) {{
        if (this == o) return true;
        if (!(o instanceof {class_name}Id)) return false;
        {class_name}Id other = ({class_name}Id) o;
        return Objects.equals(this.{first}, other.{first});
    }}

    @Override
    public int hashCode() {{
        return Objects.hash({gets});
    }}
}}
"""


def build_id_class(table_name, pk_cols, types_by_col):
    class_name = pascal(table_name)
    fields, accessors = [], []
    for col in pk_cols:
        java_type = types_by_col[col]
        fields.append(f"    private {java_type} {col};")
        cap = pascal(col)
        getter = ("is" if java_type == "Boolean" else "get") + cap
        accessors.append(f"    public {java_type} {getter}() {{ return {col}; }}")
        accessors.append(f"    public void set{cap}({java_type} {col}) "
                         f"{{ this.{col} = {col}; }}")
        accessors.append("")
    content = ID_CLASS_TEMPLATE.format(
            class_name=class_name,
            fields="\n".join(fields).rstrip("\n"),
            accessors="\n".join(accessors).rstrip("\n"),
            first=pk_cols[0],
            gets=", ".join("get" + pascal(n) + "()" for n in pk_cols))
    (OUT_DIR / f"{class_name}Id.java").write_text(content)


def build_entity(table_name, columns, pk_column, composite, table_uniques):
    class_name = pascal(table_name)
    types_by_col = {c: j for c, j, _ in columns}
    raw_types = {c: r for c, _, r in columns}
    raw_sql_types = raw_types

    unique_block = ""
    if table_uniques:
        parts = []
        for cols in sorted(table_uniques):
            names = ", ".join(f'"{c}"' for c in cols)
            parts.append(f"@UniqueConstraint(columnNames = {{{names}}})")
        unique_block = ",\n        uniqueConstraints = {" + ", ".join(parts) + "}"

    id_class_line = ""
    if composite:
        build_id_class(table_name, composite, types_by_col)
        id_class_line = f"\n@IdClass({class_name}Id.class)"

    generated_import = ("import jakarta.persistence.GeneratedValue;\n"
                        "import jakarta.persistence.GenerationType;\n"
                        if not composite and types_by_col[pk_column] == "Long" else "")

    fields, accessors = [], []
    seen = set()
    for col_name, java_type, _raw in columns:
        field_name = col_name
        while field_name in seen:
            field_name += "_"
        seen.add(field_name)
        annotations = [f'    @Column(name = "{col_name}"']
        col_def = raw_types.get(col_name)
        if java_type == "String" and col_def in ("text",):
            annotations[0] += ', columnDefinition = "text"'
        elif java_type == "String" and col_def and col_def.startswith("varchar("):
            annotations[0] += f', columnDefinition = "{col_def}"'
        elif java_type.endswith("[]"):
            annotations[0] += f', columnDefinition = "{raw_sql_types[col_name]}"'
        elif java_type == "java.math.BigDecimal" and col_def and col_def.startswith(
                "numeric"):
            annotations[0] += f', columnDefinition = "{col_def}"'
        elif "generated always as" in (col_def or "").lower():
            annotations[0] += ', insertable = false, updatable = false'
            annotations[0] += f', columnDefinition = "{col_def}"'
        annotations[0] += ')' 
        if (composite and col_name in composite) or \
           (not composite and col_name == pk_column):
            annotations.insert(0, "    @Id")
            if not composite and java_type == "Long":
                annotations.insert(0,
                        "    @GeneratedValue(strategy = GenerationType.IDENTITY)")
        fields.extend(annotations)
        fields.append(f"    private {java_type} {field_name};")
        fields.append("")
        cap = pascal(field_name)
        getter = ("is" if java_type == "Boolean" else "get") + cap
        accessors.append(f"    public {java_type} {getter}() {{ return {field_name}; }}")
        accessors.append(f"    public void set{cap}({java_type} {field_name}) "
                         f"{{ this.{field_name} = {field_name}; }}")
        accessors.append("")

    content = ENTITY_TEMPLATE.format(
            generated_import=generated_import,
            table=table_name,
            unique_block=unique_block,
            id_class_line=id_class_line,
            class_name=class_name,
            fields="\n".join(fields).rstrip("\n"),
            accessors="\n".join(accessors).rstrip("\n"))
    (OUT_DIR / f"{class_name}Entity.java").write_text(content)


def main():
    sql = SQL_PATH.read_text()
    tables = parse_tables(sql)
    uniques, composite_pks = parse_constraints(sql)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for table_name in sorted(tables):
        columns, pk_column = tables[table_name]
        build_entity(table_name, columns, pk_column,
                     composite_pks.get(table_name), uniques.get(table_name, set()))
        count += 1
    print(f"wrote {count} entities (+{len(list(OUT_DIR.glob('*Id.java')))} id classes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
