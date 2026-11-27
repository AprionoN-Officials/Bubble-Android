"""
Bubble Translation Studio — Excel Template Generator
Reads app/src/main/res/values/strings.xml (EN) and values-in/strings.xml (ID)
and produces:
  1. Multi-sheet, styled .xlsx translation template with live Excel formulas
  2. Optional pre-filled sample .xlsx (e.g. Indonesian) for immediate testing
  3. previewer/default-data.js so the 1:1 Web Previewer works offline via file://

Usage:
  python tools/i18n/export_template.py
  python tools/i18n/export_template.py --lang-code ja --lang-name "Japanese" --out Bubble-v1.1.1-ja.xlsx
"""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import openpyxl
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = Path(__file__).resolve().parents[2]
EN_XML = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
ID_XML = ROOT / "app" / "src" / "main" / "res" / "values-in" / "strings.xml"
OUT_TEMPLATES = Path(__file__).resolve().parent / "templates"
PREVIEWER_DATA_JS = Path(__file__).resolve().parent / "previewer" / "default-data.js"

PLACEHOLDER_RE = re.compile(r"%\d+\$(?:\.\d+)?[sdf]|%[sdf]")

# Map XML comment headings to clean Excel sheet tabs (<=31 chars)
SECTION_TO_SHEET = [
    ("Core App", "01_Core_Tabs"),
    ("Top Scrollable Navigation Tabs", "01_Core_Tabs"),
    ("General Segment", "02_General"),
    ("Translation Segment", "03_Translation"),
    ("Search Segment", "04_Search"),
    ("Selection Segment", "05_Selection"),
    ("Action Panel Segment", "06_Action_Panel"),
    ("Appearance & Theme Segment", "07_Appearance"),
    ("More Segment", "08_More_Privacy"),
    ("Donation Reminder", "08_More_Privacy"),
    ("Overlay & Assistant UI", "09_Overlay_UI"),
    ("Language & OCR Manager Activity", "10_Lang_Web"),
    ("Visual Search WebView", "10_Lang_Web"),
]

SHEET_LABELS = {
    "01_Core_Tabs": "Core App & 7 Navigation Tabs",
    "02_General": "General Tab (Status, Activation, Quick Settings)",
    "03_Translation": "Translation Tab (Languages, Quality, OCR)",
    "04_Search": "Search Tab (Engines, Delay, AdBlocker)",
    "05_Selection": "Selection Tab (5 Shapes, 4 Border Styles)",
    "06_Action_Panel": "Panel Tab (Action Buttons, Layout, Sliders)",
    "07_Appearance": "Appearance Tab (Theme Modes, Palettes, Language)",
    "08_More_Privacy": "More Tab (Updates, Safe Mode, About, Donation)",
    "09_Overlay_UI": "Live Overlay, Select Text, Smart Link & QR UI",
    "10_Lang_Web": "Offline Language Manager & Visual Search WebView",
}


def unescape_android_string(text: str, formatted: bool = True) -> str:
    s = text.replace("\\'", "'").replace('\\"', '"')
    if formatted:
        s = s.replace("%%", "%")
    return s


def extract_placeholders(text: str, formatted: bool = True) -> list[str]:
    if not formatted:
        return []
    return PLACEHOLDER_RE.findall(text)


def suggest_max_chars(key: str, en_text: str) -> int:
    en_len = len(en_text)
    if key.startswith(("tab_", "shape_", "border_", "pos_", "size_", "theme_", "palette_", "interval_", "app_lang_")):
        return max(16, int(en_len * 1.45))
    if key.startswith(("btn_", "action_", "menu_", "quality_", "context_", "overlay_copy", "overlay_done", "overlay_original", "overlay_translated", "overlay_tools", "panel_action_")):
        return max(20, int(en_len * 1.45))
    if key.startswith(("switch_", "label_", "section_", "scope_")):
        return max(34, int(en_len * 1.45))
    if en_len <= 30:
        return max(24, int(en_len * 1.5))
    return max(60, int(en_len * 1.45))


def map_sheet(section: str) -> str:
    for prefix, sheet in SECTION_TO_SHEET:
        if section.startswith(prefix):
            return sheet
    return "01_Core_Tabs"


def parse_android_strings(xml_path: Path) -> list[dict[str, Any]]:
    raw = xml_path.read_text(encoding="utf-8")
    # Walk line-by-line to keep XML comments aligned with <string> entries
    comment_re = re.compile(r"<!--\s*(.*?)\s*-->")
    string_re = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.DOTALL)

    entries: list[dict[str, Any]] = []
    current_section = "Core App"

    # Tokenize comments and string elements in document order
    token_re = re.compile(
        r'<!--\s*(.*?)\s*-->|<string\s+name="([^"]+)"([^>]*)>(.*?)</string>',
        re.DOTALL,
    )
    for m in token_re.finditer(raw):
        if m.group(1) is not None:
            current_section = m.group(1).strip()
        else:
            key = m.group(2)
            attrs = m.group(3) or ""
            inner_xml = m.group(4) or ""
            formatted = 'formatted="false"' not in attrs
            # Decode XML entities cleanly via ElementTree
            wrapped = ET.fromstring(f"<s {attrs}>{inner_xml}</s>")
            decoded = unescape_android_string(wrapped.text or "", formatted=formatted)
            entries.append(
                {
                    "key": key,
                    "section": current_section,
                    "sheet": map_sheet(current_section),
                    "formatted": formatted,
                    "text": decoded,
                    "placeholders": extract_placeholders(decoded, formatted=formatted),
                }
            )
    return entries


def build_workbook(
    en_entries: list[dict[str, Any]],
    id_map: dict[str, str],
    out_xlsx: Path,
    lang_code: str = "",
    lang_name: str = "",
    prefill_map: dict[str, str] | None = None,
) -> None:
    wb = openpyxl.Workbook()
    # Guide Sheet
    ws_guide = wb.active
    ws_guide.title = "00_Guide"
    ws_guide.views.sheetView[0].showGridLines = True

    # Palette (Bubble Material 3 Expressive accents)
    fill_header = PatternFill("solid", fgColor="1E2235")
    fill_accent = PatternFill("solid", fgColor="2196F3")
    fill_purple = PatternFill("solid", fgColor="5E43D6")
    fill_ref = PatternFill("solid", fgColor="F4F5F9")
    fill_edit = PatternFill("solid", fgColor="FFFDE7")
    fill_edit_hdr = PatternFill("solid", fgColor="2E7D32")
    fill_meta = PatternFill("solid", fgColor="EEF2FF")

    font_title = Font(name="Segoe UI", size=16, bold=True, color="FFFFFF")
    font_h2 = Font(name="Segoe UI", size=12, bold=True, color="1E2235")
    font_hdr = Font(name="Segoe UI", size=10, bold=True, color="FFFFFF")
    font_key = Font(name="Consolas", size=9.5, bold=True, color="374151")
    font_body = Font(name="Segoe UI", size=10, color="111827")
    font_muted = Font(name="Segoe UI", size=9.5, color="52525B")
    font_ph = Font(name="Consolas", size=9.5, bold=True, color="7C3AED")

    thin = Side(border_style="thin", color="D4D4D8")
    border_cell = Border(left=thin, right=thin, top=thin, bottom=thin)

    # Guide banner
    ws_guide.merge_cells("A1:F1")
    ws_guide["A1"] = "🫧 Bubble Android v1.1.1 — Community Translation Template"
    ws_guide["A1"].font = font_title
    ws_guide["A1"].fill = fill_header
    ws_guide["A1"].alignment = Alignment(horizontal="left", vertical="center", indent=1)
    ws_guide.row_dimensions[1].height = 38

    guide_meta = [
        ("Target Language Code (ISO, e.g. ja / ko / vi / es / ar):", lang_code or "xx"),
        ("Target Language Name (e.g. Japanese / Español):", lang_name or "Your Language"),
        ("Translator / Contributor Name (GitHub @handle):", ""),
        ("Bubble App Version:", "1.1.1"),
    ]
    for idx, (label, val) in enumerate(guide_meta, start=3):
        ws_guide.cell(idx, 1, label).font = Font(name="Segoe UI", size=10, bold=True, color="1E2235")
        ws_guide.cell(idx, 1).fill = fill_meta
        ws_guide.cell(idx, 1).border = border_cell
        c_val = ws_guide.cell(idx, 2, val)
        c_val.font = Font(name="Segoe UI", size=10, bold=True, color="1B5E20")
        c_val.fill = fill_edit if idx < 6 else fill_ref
        c_val.border = border_cell
        ws_guide.row_dimensions[idx].height = 22

    instructions = [
        "HOW TO TRANSLATE (NO CODING NEEDED) / CARA MENERJEMAHKAN:",
        "1. Fill in the yellow 'Target Language Code', 'Name', and your GitHub @handle above.",
        "2. Open each numbered sheet tab below (01_Core_Tabs through 10_Lang_Web).",
        "3. ONLY edit the light-yellow 'Your Translation' column (Column F). Do NOT modify 'string_key' (Column A).",
        "4. Keep placeholders like %1$s, %1$d, %2$d, or \\n intact! The 'Status' column warns automatically if a placeholder is missing or text is too long.",
        "5. Drag & drop this saved .xlsx file into tools/i18n/previewer/index.html to preview your translation 1:1 on a Bubble Android phone mockup & export strings.xml!",
    ]
    for i, line in enumerate(instructions, start=8):
        ws_guide.merge_cells(start_row=i, start_column=1, end_row=i, end_column=6)
        cell = ws_guide.cell(i, 1, line)
        cell.font = font_h2 if i == 8 else font_body
        cell.alignment = Alignment(wrap_text=True, vertical="center")
        ws_guide.row_dimensions[i].height = 22 if i != 13 else 30

    # Progress Dashboard Table on Guide sheet
    ws_guide.cell(15, 1, "Sheet").font = font_hdr
    ws_guide.cell(15, 2, "Section Description").font = font_hdr
    ws_guide.cell(15, 3, "Total Strings").font = font_hdr
    ws_guide.cell(15, 4, "Translated").font = font_hdr
    ws_guide.cell(15, 5, "Progress").font = font_hdr
    ws_guide.cell(15, 6, "Warnings").font = font_hdr
    for col in range(1, 7):
        ws_guide.cell(15, col).fill = fill_purple
        ws_guide.cell(15, col).alignment = Alignment(horizontal="center", vertical="center")
        ws_guide.cell(15, col).border = border_cell
    ws_guide.row_dimensions[15].height = 24

    grouped: dict[str, list[dict[str, Any]]] = {}
    for e in en_entries:
        grouped.setdefault(e["sheet"], []).append(e)

    sheet_order = list(SHEET_LABELS.keys())
    dash_row = 16
    for sheet_name in sheet_order:
        items = grouped.get(sheet_name, [])
        if not items:
            continue
        last_row = len(items) + 1

        ws = wb.create_sheet(title=sheet_name)
        ws.freeze_panes = "A2"
        ws.auto_filter.ref = f"A1:H{last_row}"

        headers = [
            "string_key",
            "Section / Context",
            "Placeholders",
            "Max Chars",
            "English (EN)",
            "Indonesian (ID)",
            "Your Translation",
            "Status / Check",
        ]
        widths = [30, 26, 18, 11, 44, 44, 46, 26]
        ws.row_dimensions[1].height = 26
        for c_idx, (h, w) in enumerate(zip(headers, widths), start=1):
            cell = ws.cell(1, c_idx, h)
            cell.font = font_hdr
            cell.fill = fill_edit_hdr if c_idx == 7 else fill_header
            cell.alignment = Alignment(horizontal="center" if c_idx in (3, 4, 8) else "left", vertical="center")
            cell.border = border_cell
            ws.column_dimensions[get_column_letter(c_idx)].width = w

        for r_offset, item in enumerate(items, start=2):
            key = item["key"]
            en_text = item["text"]
            id_text = id_map.get(key, "")
            phs = item["placeholders"]
            ph_str = ", ".join(phs)
            max_c = suggest_max_chars(key, en_text)
            prefill = prefill_map.get(key, "") if prefill_map else ""

            row_vals = [key, item["section"], ph_str, max_c, en_text, id_text, prefill]
            for c_idx, val in enumerate(row_vals, start=1):
                c = ws.cell(r_offset, c_idx, val)
                c.border = border_cell
                if c_idx == 1:
                    c.font = font_key
                    c.fill = fill_ref
                    c.alignment = Alignment(vertical="top")
                elif c_idx == 2:
                    c.font = font_muted
                    c.fill = fill_ref
                    c.alignment = Alignment(wrap_text=True, vertical="top")
                elif c_idx == 3:
                    c.font = font_ph
                    c.fill = fill_ref
                    c.alignment = Alignment(horizontal="center", vertical="top")
                elif c_idx == 4:
                    c.font = font_muted
                    c.fill = fill_ref
                    c.alignment = Alignment(horizontal="center", vertical="top")
                elif c_idx in (5, 6):
                    c.font = font_body
                    c.fill = fill_ref
                    c.alignment = Alignment(wrap_text=True, vertical="top")
                elif c_idx == 7:
                    c.font = Font(name="Segoe UI", size=10, bold=False, color="0F172A")
                    c.fill = fill_edit
                    c.alignment = Alignment(wrap_text=True, vertical="top")

            # Live Excel validation formula checking empty, missing placeholders, and max length
            ph_checks = [f'ISERROR(FIND("{p}",G{r_offset}))' for p in phs]
            if ph_checks:
                ph_expr = f"OR({','.join(ph_checks)})"
                formula = (
                    f'=IF(TRIM(G{r_offset})="","Pending",'
                    f'IF({ph_expr},"ERROR: Missing placeholder",'
                    f'IF(LEN(G{r_offset})>D{r_offset},"WARN: Long ("&LEN(G{r_offset})&"/"&D{r_offset}&")","OK")))'
                )
            else:
                formula = (
                    f'=IF(TRIM(G{r_offset})="","Pending",'
                    f'IF(LEN(G{r_offset})>D{r_offset},"WARN: Long ("&LEN(G{r_offset})&"/"&D{r_offset}&")","OK"))'
                )
            c_st = ws.cell(r_offset, 8, formula)
            c_st.font = Font(name="Segoe UI", size=9.5, bold=True, color="334155")
            c_st.fill = fill_ref
            c_st.alignment = Alignment(horizontal="center", vertical="top")
            c_st.border = border_cell

            # Comfortable row height based on longest text
            max_lines = max(
                1,
                int(len(en_text) / 42) + en_text.count("\\n") + 1,
                int(len(id_text) / 42) + id_text.count("\\n") + 1,
            )
            ws.row_dimensions[r_offset].height = min(130, max(22, max_lines * 16 + 6))

        # Populate Guide summary row
        ws_guide.cell(dash_row, 1, sheet_name).font = font_key
        ws_guide.cell(dash_row, 2, SHEET_LABELS[sheet_name]).font = font_body
        ws_guide.cell(dash_row, 3, len(items)).alignment = Alignment(horizontal="center")
        ws_guide.cell(dash_row, 4, f'=COUNTA({sheet_name}!G2:G{last_row})').alignment = Alignment(horizontal="center")
        c_pct = ws_guide.cell(dash_row, 5, f"=IF(C{dash_row}=0,0,D{dash_row}/C{dash_row})")
        c_pct.number_format = "0%"
        c_pct.alignment = Alignment(horizontal="center")
        ws_guide.cell(
            dash_row,
            6,
            f'=COUNTIF({sheet_name}!H2:H{last_row},"ERROR*")+COUNTIF({sheet_name}!H2:H{last_row},"WARN*")',
        ).alignment = Alignment(horizontal="center")
        for col in range(1, 7):
            ws_guide.cell(dash_row, col).border = border_cell
        ws_guide.row_dimensions[dash_row].height = 21
        dash_row += 1

    # Totals row
    ws_guide.cell(dash_row, 1, "TOTAL").font = Font(name="Segoe UI", size=10, bold=True)
    ws_guide.cell(dash_row, 2, "All Bubble Android v1.1.1 Strings").font = Font(name="Segoe UI", size=10, bold=True)
    ws_guide.cell(dash_row, 3, f"=SUM(C16:C{dash_row-1})").alignment = Alignment(horizontal="center")
    ws_guide.cell(dash_row, 4, f"=SUM(D16:D{dash_row-1})").alignment = Alignment(horizontal="center")
    c_tot_pct = ws_guide.cell(dash_row, 5, f"=IF(C{dash_row}=0,0,D{dash_row}/C{dash_row})")
    c_tot_pct.number_format = "0.0%"
    c_tot_pct.alignment = Alignment(horizontal="center")
    ws_guide.cell(dash_row, 6, f"=SUM(F16:F{dash_row-1})").alignment = Alignment(horizontal="center")
    for col in range(1, 7):
        ws_guide.cell(dash_row, col).fill = fill_meta
        ws_guide.cell(dash_row, col).border = border_cell
        ws_guide.cell(dash_row, col).font = Font(name="Segoe UI", size=10, bold=True, color="1E2235")

    guide_widths = [44, 42, 14, 14, 14, 14]
    for col, w in enumerate(guide_widths, start=1):
        ws_guide.column_dimensions[get_column_letter(col)].width = w

    out_xlsx.parent.mkdir(parents=True, exist_ok=True)
    wb.save(out_xlsx)


def write_previewer_default_js(en_entries: list[dict[str, Any]], id_map: dict[str, str]) -> None:
    payload = {
        "version": "1.1.1",
        "totalKeys": len(en_entries),
        "sheets": SHEET_LABELS,
        "entries": [
            {
                "key": e["key"],
                "section": e["section"],
                "sheet": e["sheet"],
                "formatted": e["formatted"],
                "placeholders": e["placeholders"],
                "maxChars": suggest_max_chars(e["key"], e["text"]),
                "en": e["text"],
                "id": id_map.get(e["key"], ""),
            }
            for e in en_entries
        ],
    }
    PREVIEWER_DATA_JS.parent.mkdir(parents=True, exist_ok=True)
    PREVIEWER_DATA_JS.write_text(
        "// Auto-generated by tools/i18n/export_template.py — DO NOT EDIT MANUALLY\n"
        f"window.BUBBLE_DEFAULT_I18N = {json.dumps(payload, ensure_ascii=False, indent=2)};\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Bubble Android Excel translation templates.")
    parser.add_argument("--lang-code", default="", help="Optional target language ISO code (e.g. ja)")
    parser.add_argument("--lang-name", default="", help="Optional target language name (e.g. Japanese)")
    parser.add_argument("--out", default="", help="Optional custom output .xlsx path")
    parser.add_argument("--with-sample-id", action="store_true", help="Also generate pre-filled Indonesian sample .xlsx")
    args = parser.parse_args()

    en_entries = parse_android_strings(EN_XML)
    id_entries = parse_android_strings(ID_XML)
    id_map = {e["key"]: e["text"] for e in id_entries}

    OUT_TEMPLATES.mkdir(parents=True, exist_ok=True)
    out_path = Path(args.out) if args.out else (OUT_TEMPLATES / "Bubble-v1.1.1-Translation-Template.xlsx")
    build_workbook(en_entries, id_map, out_path, lang_code=args.lang_code, lang_name=args.lang_name)
    print(f"Generated blank template ({len(en_entries)} strings): {out_path}")

    if args.with_sample_id:
        sample_path = OUT_TEMPLATES / "Bubble-v1.1.1-Sample-ID.xlsx"
        build_workbook(
            en_entries,
            id_map,
            sample_path,
            lang_code="id",
            lang_name="Bahasa Indonesia",
            prefill_map=id_map,
        )
        print(f"Generated pre-filled ID sample: {sample_path}")

    write_previewer_default_js(en_entries, id_map)
    print(f"Updated 1:1 previewer baseline data: {PREVIEWER_DATA_JS}")


if __name__ == "__main__":
    main()
