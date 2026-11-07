# Aura Browser

Android WebView browser. Built with GitHub Actions (no local setup needed).

## Build
1. Create a new GitHub repo and upload all files from this folder (keep the `.github` folder).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow** (it also runs on every push).
3. When it finishes, download the **AuraBrowser-debug-apk** artifact and install the APK.

## Features
- Home page with search box, voice search, quick actions, shortcuts (add/remove your own)
- Bookmarks (sort, search, import/export HTML), History, multiple tabs, Private tabs
- **Downloads page** (progress, open, share, copy link, retry, cancel, delete)
- **Ad blocker**: domain lists (auto-updated), pattern rules, pop-up / click-hijack killer
- **Ad blocker**: ~150 ad/tracker domains, hide empty ad boxes, pop-up blocker,
  per-site allow list, blocked counter
- **Manual ad blocker**: tap any element on a page, widen with "Parent", press "Hide"
  (saved per site, manage under Settings / Hidden elements); add your own blocked domains
- Long-press menu on links and images (new tab, private tab, copy, share, download)
- Reader mode, dark mode, translate, find in page, print/PDF, screenshot, page source,
  desktop site, text size, brightness, block images, full screen
- Settings: search engine (Google/Bing/DuckDuckGo/Brave/Yahoo), HTTPS-only, Safe Browsing,
  JavaScript on/off, block third-party cookies, clear data on exit, keep screen on
- Can be set as a default browser (handles http/https links)

## Tools menu (v1.2)
All 30 menu options are wired up:
- **Image viewer** (grid, multi-select, zoom preview, save/share/edit), **Save all images** (to Pictures/AuraBrowser)
- **Read aloud** (floating pause/stop/speed bar) and **Text to Speech** (own text, speed, pitch, language, save as WAV)
- **Add current page** (home shortcuts / bookmarks / phone home screen), **Save page** (.mht / .html / PDF)
- **DNS** filter via DNS-over-HTTPS (Cloudflare Security/Family, AdGuard, Google, custom) + shortcut to system Private DNS
- **Block area**, **Screen filter** (colour tint + strength), **Text size** slider, **Text zoom in** (+10%)
- **Image editor** (rotate, flip, crop, brightness/contrast/saturation, filters, draw), **One-handed mode** (shrinks the UI to a corner, switch side)
- **Memo** (notes with optional page link; new memo from a page pre-fills title, link and selected text)

## v1.7
- Blocked ad images / iframes / videos no longer leave an empty gap: the ad and its empty wrappers are collapsed
- Stronger video-ad blocking (more video ad networks, VAST/pre-roll URLs, in-player ad skip, floating video ads)
- No more blue full-screen flash when tapping a video/link (tap highlight removed in the page and in iframes)
- **Download video** button appears when a video plays; lists all found qualities (MP4/WebM direct, HLS .m3u8 with 1080p/720p/...)

## v1.8
- Empty ad slots (blocked script left an empty box) are collapsed; "hollow" wrappers detected better
- YouTube ad data is stripped before the player sees it (plus existing skip logic); Google IMA video-ad SDK blocked
- Tap highlight / text-selection flash removed on video, image, link, button (page + iframes)
