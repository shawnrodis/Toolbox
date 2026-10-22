# Toolbox Sync via Firebase (free, no server to run)

The other way to sync Toolbox across your devices — no home server,
no port forwarding, nothing to keep running. You create a free
Firebase project (Google's backend service), paste its config into
Toolbox once, and sign in with an email + password you choose. Both
editions (Android app and web page) talk to the same database, so a
change on one device shows up on the other.

Your data lives in **your own** Firebase project, under your own
account, protected by rules that only let your signed-in account
read or write it. Nobody else's Toolbox can see it, and the app
developer can't either.

That includes the API keys you type into Settings (Aviationstack and
the two Gemini keys): with sync on, a key entered on one device is
stored in your project and appears on your other signed-in devices —
that's what lets you enter a key once and have it everywhere. Keys
you entered *before* turning sync on are uploaded on the first sync
too, and clearing a key on one device clears it on the others.

The free **Spark** plan is more than enough — Toolbox sync is a few
kilobytes of settings, nowhere near the free limits.

## 1. Create the project

1. Go to <https://console.firebase.google.com> and sign in with any
   Google account.
2. **Add project** → give it any name (e.g. `toolbox-sync`) → you can
   decline Google Analytics → **Create project**.

## 2. Create the Realtime Database

1. In the left sidebar: **Build → Realtime Database → Create database**.
2. Location: leave the default.
3. Security rules: choose **Start in locked mode** → **Enable**.
   (Locked mode denies everyone until you paste the real rules in
   step 4 — that's what we want.)

## 3. Turn on email/password sign-in

1. **Build → Authentication → Get started**.
2. **Sign-in method** tab → **Email/Password** → enable the first
   toggle (**Email/Password**) → **Save**.

You don't create the account here — you'll do that inside Toolbox
(Step 6), so the same account works from both editions.

## 4. Paste the security rules

1. **Build → Realtime Database → Rules** tab.
2. Replace everything in the editor with exactly this:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

3. **Publish**.

This says: a signed-in user can only ever touch the folder with
their own user id on it. Everything else is denied.

## 5. Copy the web config

1. Click the gear icon next to **Project Overview → Project settings**.
2. Scroll to **Your apps**. If no web app exists yet, click the
   **`</>`** (web) icon, give it any nickname (e.g. `Toolbox`),
   leave "Firebase Hosting" unticked, and **Register app**.
3. You'll see a `firebaseConfig = { ... }` block. Copy the whole
   thing — braces and all.

> The `apiKey` in that config is an identifier for your project, not
> a password — it's meant to ship in apps. Your data is protected by
> the sign-in from step 3 and the rules from step 4, not by hiding
> the config.

## 6. Paste it into Toolbox

On each device (Android app and/or web page):

1. **Settings → Sync Across Devices → Provider → Firebase**.
2. Paste the config into the **Firebase config** field → **Save config**.
   Toolbox reads the `apiKey` and `databaseURL` out of it and shows
   you what it found.
3. Enter an **email + password** (invent a password just for this —
   at least 6 characters) → **Sign up** the first time.
   On your other devices, use **Sign in** with the same email +
   password instead.
4. That's it — sync starts immediately, and the Auto-sync interval
   below works the same as it does for the home server.

Sign out any time from the same card; your data stays in Firebase,
and signing back in picks up right where you left off.

## How it works (for the curious)

- Toolbox stores the same sync state map the home server uses at
  `users/<your-uid>/state.json` in the Realtime Database, through
  Firebase's plain REST API — no Firebase SDK in either edition.
- Sign-in uses the Identity Toolkit REST endpoints; the app keeps
  the long-lived refresh token on the device only (it is never
  synced anywhere) and quietly exchanges it for a fresh session
  token before syncing. If a session ever expires for good, the
  status line will simply ask you to sign in again.
