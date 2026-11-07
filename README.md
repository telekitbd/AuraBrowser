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
- **Adult site blocker**: domain list + keyword filter + adult search block + forced SafeSearch,
  optional parental PIN, your own blocked-sites list
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
