# Custom Search Engine Guide — Bubble Android

**English** | [Bahasa Indonesia](Custom-Engine_ID.md)

Bubble lets you add your own **Visual (Image) Search Engines** and **Selected-Text Web Search Engines** directly inside the **Search** tab without rebuilding the app.

---

## 1. Where to Add a Custom Engine

1. Open **Bubble** → tap the **Search** tab in the top navigation bar.
2. Scroll to:
   - **Visual Search Engines** → tap **+ Add Custom Engine** (for reverse image search), or
   - **Selected-Text Web Search Engines** → tap **+ Add Custom Engine** (for searching highlighted text in *Select Text* mode).
3. Drag the **`=`** handle next to any engine (or tap **`=`** to pick an exact position) to reorder your list, and tap **Default** to make it your primary engine.

---

## 2. Custom Visual (Image) Search Engines

Bubble supports **two methods** for custom reverse image search engines:

### Method A — Web Visual Search (`https://...`)

Leave the **Android Package** field blank and enter a valid `http://` or `https://` URL that accepts image file uploads.

When you search an image, Bubble crops your selection, optimizes it according to your **Image Quality Preset** (*Original / Balanced / Data Saver*), and opens the site inside Bubble's built-in WebView (or your external browser if *Open Web Engines in External Browser* is enabled), automatically handing the temporary image to the page's file input when supported.

| Field | Requirement | Example |
| --- | --- | --- |
| **Engine Name** | 2–48 characters | `IQDB` |
| **Base URL** | Must start with `https://` or `http://` | `https://iqdb.org/` |
| **Android Package** | Leave **blank** | *(empty)* |

#### Popular Web Visual Search Examples

| Engine Name | Base URL | Best For |
| --- | --- | --- |
| **IQDB** | `https://iqdb.org/` | Anime / manga illustration lookup |
| **ASCII2D** | `https://ascii2d.net/` | Pixiv / Twitter Japanese artwork |
| **Lenso.ai** | `https://lenso.ai/` | General reverse image & object lookup |
| **PimEyes** | `https://pimeyes.com/` | Public web image lookup |
| **Karma Decay** | `http://karmadecay.com/` | Reddit image lookup |

> **Important Note:** Bubble never bypasses CAPTCHAs or anti-bot checks. If a website shows a human verification prompt or requires tapping an upload button manually, complete it directly in the page or tap **Browser** in the top bar.

---

### Method B — Android App Package Intent (`com.example.app`)

If you have an installed Android app that accepts shared images via `Intent.ACTION_SEND` (`image/*`), enter its **Android Package Name** in the third field.

| Field | Requirement | Example |
| --- | --- | --- |
| **Engine Name** | 2–48 characters | `Pinterest Lens` |
| **Base URL** | Optional fallback URL (`https://...`) | `https://www.pinterest.com/` |
| **Android Package** | Valid Java/Android package ID (`a.b` format) | `com.pinterest` |

#### How to Find an App's Package Name
- Open the app's **Google Play Store** link in a browser — the package name is the `id=` value in the URL:
  `https://play.google.com/store/apps/details?id=com.pinterest` → **`com.pinterest`**
- Or use any installed App Info / Package Viewer utility.

> **Fallback Behavior:** If the target app is not installed or does not accept direct image intents, Bubble automatically falls back to Android's native **Open With** chooser so your search never fails silently.

---

## 3. Custom Selected-Text Search Engines (`{query}`)

Selected-Text search engines are used when you highlight plain text inside **Select Text** mode and tap **Web Search** (note: if the selected text is a URL/link, Bubble automatically turns that button into **Open Link** instead).

| Field | Requirement | Example |
| --- | --- | --- |
| **Engine Name** | 2–48 characters | `Brave Search` |
| **Query URL Template** | Valid `http://`/`https://` URL containing `{query}` | `https://search.brave.com/search?q={query}` |

Bubble automatically URL-encodes your selected text and replaces `{query}` before opening the result in your browser.

#### Ready-to-Use Text Search Templates

| Engine Name | Query URL Template |
| --- | --- |
| **Brave Search** | `https://search.brave.com/search?q={query}` |
| **Startpage** | `https://www.startpage.com/sp/search?query={query}` |
| **Kagi** | `https://kagi.com/search?q={query}` |
| **YouTube** | `https://www.youtube.com/results?search_query={query}` |
| **GitHub** | `https://github.com/search?q={query}` |
| **Wikipedia (ID)** | `https://id.wikipedia.org/wiki/Special:Search?search={query}` |
| **Jisho (Japanese Dictionary)** | `https://jisho.org/search/{query}` |
| **KBBI Daring (Indonesian)** | `https://kbbi.kemdikbud.go.id/entri/{query}` |

---

## 4. Validation Rules & Troubleshooting

| Error / Issue | Cause & Fix |
| --- | --- |
| *"Engine name must be 2–48 characters."* | Use a concise name between 2 and 48 characters. |
| *"Valid HTTP/HTTPS Base URL is required…"* | Make sure the URL starts with `https://` or `http://` with no spaces. |
| *"Text search template must include `{query}`"* | Include `{query}` (lowercase, inside curly braces) where the search keywords belong. |
| *"Invalid Android package name"* | Use dot-separated package format like `com.vendor.app` with no spaces or symbols. |
| **Image upload rejected as too large** | Lower **Image Quality Preset** to **Balanced** or **Data Saver** (or reduce *Max Image Resolution*) in the **Search** tab. |
| **Want to restore built-in engines?** | Scroll to the bottom of the **Search** tab and tap **Reset to Default**. |
