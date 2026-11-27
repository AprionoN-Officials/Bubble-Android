"""
Bubble Translation Studio — Excel/CSV -> Android strings.xml Converter & Validator
Reads a translated .xlsx (from export_template.py) or .csv file, validates all
Android format placeholders (%1$s, %1$d, etc.) and button length guidelines,
and outputs a clean Android values-<lang>/strings.xml ready to drop into the app.

Usage:
  python tools/i18n/import_excel.py tools/i18n/templates/Bubble-v1.1.1-Sample-ID.xlsx --out tools/i18n/out/strings-id.xml
  python tools/i18n/import_excel.py my-ja.xlsx --check-only
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Any
from xml.sax.saxutils import escape as xml_escape

import openpyxl

from export_template import EN_XML, PLACEHOLDER_RE, parse_android_strings, suggest_max_chars


def escape_android_xml(text: str, formatted: bool = True) -> str:
    # Escape &, <, > first
    s = xml_escape(text, entities={'"': '\\"'})
    # Escape apostrophes for Android string resources
    s = s.replace("'", "\\'")
    # Escape standalone literal % (not part of valid format specifiers) when formatted=true
    if formatted:
        placeholders: list[str] = []

        def _hold(m: re.Match[str]) -> str:
            placeholders.append(m.group(0))
            return f"__PH_{len(placeholders) - 1}__"

        s = PLACEHOLDER_RE.sub(_hold, s)
        s = s.replace("%", "%%")
        for idx, ph in enumerate(placeholders):
            s = s.replace(f"__PH_{idx}__", ph)
    return s


def read_translations_xlsx(xlsx_path: Path) -> tuple[dict[str, str], dict[str, str]]:
    wb = openpyxl.load_workbook(xlsx_path, data_only=True)
    meta = {"lang_code": "", "lang_name": "", "contributor": ""}
    if "00_Guide" in wb.sheetnames:
        g = wb["00_Guide"]
        meta["lang_code"] = str(g["B3"].value or "").strip()
        meta["lang_name"] = str(g["B4"].value or "").strip()
        meta["contributor"] = str(g["B5"].value or "").strip()

    translations: dict[str, str] = {}
    for name in wb.sheetnames:
        if name == "00_Guide":
            continue
        ws = wb[name]
        headers = [str(ws.cell(1, c).value or "").strip().lower() for c in range(1, ws.max_column + 1)]
        if "string_key" not in headers:
            continue
        key_col = headers.index("string_key") + 1
        # Prefer "your translation" column, fallback to column 7
        val_col = next((i + 1 for i, h in enumerate(headers) if "translation" in h), 7)
        for r in range(2, ws.max_row + 1):
            key = str(ws.cell(r, key_col).value or "").strip()
            val = ws.cell(r, val_col).value
            if key and val is not None and str(val).strip() != "":
                translations[key] = str(val).strip()
    return translations, meta


def read_translations_csv(csv_path: Path) -> tuple[dict[str, str], dict[str, str]]:
    translations: dict[str, str] = {}
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            key = (row.get("string_key") or row.get("key") or "").strip()
            val = (row.get("Your Translation") or row.get("translation") or "").strip()
            if key and val:
                translations[key] = val
    return translations, {"lang_code": "", "lang_name": "", "contributor": ""}


def validate_translations(
    en_entries: list[dict[str, Any]], translations: dict[str, str]
) -> dict[str, Any]:
    errors: list[dict[str, Any]] = []
    warnings: list[dict[str, Any]] = []
    translated_count = 0

    for item in en_entries:
        key = item["key"]
        val = translations.get(key)
        if not val:
            continue
        translated_count += 1

        if item["formatted"]:
            exp_ph = sorted(item["placeholders"])
            got_ph = sorted(PLACEHOLDER_RE.findall(val))
            if exp_ph != got_ph:
                errors.append(
                    {
                        "key": key,
                        "sheet": item["sheet"],
                        "expected_placeholders": exp_ph,
                        "actual_placeholders": got_ph,
                        "translation": val,
                    }
                )

        max_c = suggest_max_chars(key, item["text"])
        if len(val) > int(max_c * 1.25):
            warnings.append(
                {
                    "key": key,
                    "sheet": item["sheet"],
                    "length": len(val),
                    "recommended_max": max_c,
                    "translation": val,
                }
            )

    unknown_keys = sorted(set(translations.keys()) - {e["key"] for e in en_entries})
    return {
        "total_keys": len(en_entries),
        "translated_keys": translated_count,
        "coverage_pct": round((translated_count / max(1, len(en_entries))) * 100.0, 1),
        "errors": errors,
        "warnings": warnings,
        "unknown_keys": unknown_keys,
    }


def render_android_xml(
    en_entries: list[dict[str, Any]],
    translations: dict[str, str],
    include_untranslated_fallback: bool = False,
) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    last_section = None
    for item in en_entries:
        key = item["key"]
        val = translations.get(key)
        if not val:
            if not include_untranslated_fallback:
                continue
            val = item["text"]

        if item["section"] != last_section:
            if last_section is not None:
                lines.append("")
            lines.append(f"    <!-- {item['section']} -->")
            last_section = item["section"]

        escaped = escape_android_xml(val, formatted=item["formatted"])
        fmt_attr = '' if item["formatted"] else ' formatted="false"'
        lines.append(f'    <string name="{key}"{fmt_attr}>{escaped}</string>')

    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate & convert Bubble translation Excel/CSV to strings.xml")
    parser.add_argument("input_file", help="Path to translated .xlsx or .csv file")
    parser.add_argument("--out", default="", help="Output Android strings.xml path")
    parser.add_argument("--json-out", default="", help="Optional JSON report path")
    parser.add_argument("--include-fallback", action="store_true", help="Include English fallback for untranslated keys")
    parser.add_argument("--check-only", action="store_true", help="Validate only without writing XML")
    args = parser.parse_args()

    in_path = Path(args.input_file)
    if not in_path.exists():
        print(f"ERROR: File not found: {in_path}", file=sys.stderr)
        sys.exit(1)

    en_entries = parse_android_strings(EN_XML)
    if in_path.suffix.lower() == ".csv":
        translations, meta = read_translations_csv(in_path)
    else:
        translations, meta = read_translations_xlsx(in_path)

    report = validate_translations(en_entries, translations)
    print(f"=== Bubble Translation Validation ({in_path.name}) ===")
    if meta["lang_code"]:
        print(f"Language : {meta['lang_name']} ({meta['lang_code']})")
    print(f"Coverage : {report['translated_keys']}/{report['total_keys']} ({report['coverage_pct']}%)")
    print(f"Errors   : {len(report['errors'])} placeholder mismatch(es)")
    print(f"Warnings : {len(report['warnings'])} long string(s)")
    if report["unknown_keys"]:
        print(f"Unknown  : {len(report['unknown_keys'])} unrecognized key(s)")

    for err in report["errors"][:15]:
        print(
            f"  [ERROR] {err['sheet']} :: {err['key']} — expected {err['expected_placeholders']}, got {err['actual_placeholders']}"
        )
    for w in report["warnings"][:10]:
        print(f"  [WARN]  {w['sheet']} :: {w['key']} — {w['length']} chars (recommended <= {w['recommended_max']})")

    if args.json_out:
        Path(args.json_out).write_text(json.dumps({"meta": meta, "report": report}, indent=2, ensure_ascii=False), encoding="utf-8")

    if report["errors"]:
        print("\nFAILED: Fix placeholder errors before generating Android strings.xml.", file=sys.stderr)
        sys.exit(2)

    if not args.check_only:
        out_xml = Path(args.out) if args.out else (in_path.with_suffix(".strings.xml"))
        out_xml.parent.mkdir(parents=True, exist_ok=True)
        xml_content = render_android_xml(
            en_entries,
            translations,
            include_untranslated_fallback=args.include_fallback,
        )
        out_xml.write_text(xml_content, encoding="utf-8")
        print(f"\n✓ Valid Android strings.xml written to: {out_xml}")


if __name__ == "__main__":
    main()
