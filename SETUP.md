# Checklist — setup guide

A home-screen widget for quick-capture checklist notes that auto-sort into
categories and subcategories, synced through a small Cloudflare Worker so
the data survives a phone change.

## How it actually works (read this first)

Two things about Android make this different from your other single-file
PWAs (Lift Daddy, the boat checklist):

1. **Android widgets can't contain a real text box.** The "+ Add" button on
   the widget opens `CaptureActivity` — a small floating window that sits on
   top of your home screen. You type there, hit Save, and it closes. That's
   also how Google Keep/Tasks widgets work; there's no way to type directly
   into the widget surface itself.
2. **This is a compiled Android app, not a website.** There's no
   `index.html` to open — GitHub Actions compiles the Kotlin into an APK
   file you install on your phone (sideloading, since it's not on the Play
   Store).

## Shorthand rule

Type: `<Category> <Subcategory> <the rest of your note>`

Example: `Fishing Organisation buy new hooks` → creates (or reuses) a
**Fishing** category, a **Organisation** subcategory inside it, and adds
"buy new hooks" as a checklist item there.

- Matching is case-insensitive — `fishing` and `Fishing` land in the same
  folder.
- Any category/subcategory word not seen before is created automatically.
- You need at least 3 words (category, subcategory, and the note itself) —
  fewer than that and it'll tell you to add more instead of silently
  creating a broken entry.

## 1. Push this to GitHub

```bash
cd checklist-widget-app
git init
git add .
git commit -m "Initial checklist app"
gh repo create checklist-widget-app --private --source=. --push
# or manually: create an empty repo on github.com, then
#   git remote add origin git@github.com:<you>/checklist-widget-app.git
#   git branch -M main
#   git push -u origin main
```

## 2. Deploy the Cloudflare Worker (the sync backend)

This is the same Cloudflare account you already use for Lift Daddy/boat
checklist — just a Worker + D1 database instead of Pages.

```bash
cd backend
npm install
npx wrangler login                      # opens a browser to authorize

npx wrangler d1 create checklist        # prints a database_id
```

Copy the `database_id` it prints into `backend/wrangler.toml`, replacing
`REPLACE_WITH_YOUR_D1_DATABASE_ID`.

```bash
npm run db:migrate                      # applies schema.sql to the new DB

npx wrangler secret put API_TOKEN
# paste/type a long random string when prompted — this is the password
# your phone uses to talk to the Worker. Generate one with e.g.:
#   openssl rand -hex 24

npm run deploy                          # prints your Worker URL, e.g.
# https://checklist-api.<your-subdomain>.workers.dev
```

Keep that Worker URL and the API_TOKEN you chose — you'll type both into
the app once it's installed.

## 3. Build the APK

Push to `main` triggers `.github/workflows/build-apk.yml`, which compiles
the app and attaches `checklist-debug.apk` to a GitHub Release tagged
`latest-apk` in your repo (Releases tab). Every push to `main` updates that
same release, so it's always the same download link.

If the very first build fails: open the failed run's log in the Actions
tab and send me the error — dependency versions occasionally need a small
bump and I can't fully test-compile this without a working Android SDK on
my end (I don't have network access to Google's Maven repo from where I
build), so this first CI run is effectively the real compile check.

**No `gradlew` is committed** — same network restriction meant I couldn't
fetch the wrapper jar. The workflow installs Gradle itself, so this doesn't
affect the APK build. If you later open the project in Android Studio, it
will offer to generate the wrapper for you the first time you sync (or run
`gradle wrapper` yourself with your own internet access).

## 4. Install it on your phone

1. On your phone, go to the repo's **Releases** page (or download the APK
   another way) and download `checklist-debug.apk`.
2. Android will likely block the install the first time — tap through to
   **Settings** and allow installs from that source (Files app / your
   browser, whichever you used to open the APK). This is normal for any
   app installed outside the Play Store.
3. Open the app once, tap the settings (gear) icon, and enter:
   - **Worker URL**: from step 2 (e.g. `https://checklist-api.you.workers.dev`)
   - **API token**: the value you set with `wrangler secret put API_TOKEN`
4. Long-press your home screen → **Widgets** → **Checklist** → drag it onto
   a home screen, same as you'd add the calendar widget.

## 5. Using it day to day

- Tap **+ Add** on the widget (or the + button in the app) → type
  `Category Subcategory note text` → Save.
- Open the app to see everything grouped by category/subcategory, tick
  items off, or delete them.
- It works offline — notes save locally immediately and sync in the
  background once you've got signal; the refresh icon in the app forces an
  immediate sync.

## Repo layout

```
app/       Android app (Kotlin, Jetpack Compose + Glance widget, Room, Retrofit)
backend/   Cloudflare Worker + D1 (the sync API)
```
