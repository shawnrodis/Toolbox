# Run your own Toolbox sync server

Toolbox Sync is deliberately self-hosted. Toolbox does not provide a sync server, and neither edition has a server address built in. You run a tiny server, choose a secret token, and enter the server address and token in Toolbox on each device.

The Android app and the single-file web app speak the **same sync protocol**, described below. A complete reference server is included in this guide.

## How it works

Each Toolbox installation keeps its data locally. When Sync is configured:

1. Local changes to synced keys are marked pending.
2. After a short pause — 2.5 seconds in the app's current implementation — Toolbox pushes the pending changes to your server.
3. Toolbox also pulls the server state. A pull runs before a push for **Sync Now** and for scheduled/start-up sync ticks.
4. If the server's copy of a key is newer than the last copy this device saw, the device applies it.
5. A key that has an unsynced local change is never overwritten by a pull.
6. If a previously synced key is absent from the server state, the device deletes its local copy of that key.

The server is a simple state store: it does not merge individual fields inside a value. Values are stored as the exact strings Toolbox sends.

> **Protect the token and the server.** The token is both the password and the namespace for a sync state. The synced data can include your saved Aviationstack and Gemini API-key fields, so treat the server's data file as sensitive too.

## App-side setup

1. Start the reference server below, or another server that implements the contract exactly.
2. In Toolbox, open **Settings → Sync Across Devices**.
3. In **Sync Server URL**, enter only the base address — for example:
   - LAN: `http://192.168.0.10:9000`
   - Internet through an HTTPS reverse proxy: `https://sync.example.com`
   
   Do **not** add `/state`; Toolbox appends that path itself. A trailing slash is trimmed by the app, but leaving it off is clearest.
4. In **Sync Token**, enter the same token you configured for the server. Use a long random value that you do not reuse elsewhere.
5. Save the settings.
6. Tap **Sync Now** and repeat the settings on your other devices, using the same server address and token.

Both a non-empty server URL and a non-empty token are required. If either is blank, Toolbox makes no sync network calls.

## Server contract

Let `BASE` be the exact base address entered in Toolbox. The only endpoint is:

```text
{BASE}/state
```

| Part | Contract |
|---|---|
| Pull | `GET {BASE}/state` |
| Push | `POST {BASE}/state` |
| Request content type | `application/json` |
| Authentication header | `X-Toolbox-Token: <token>` on both GET and POST |
| Authentication in POST body | The JSON body also contains `"token": "<token>"`; it must match the header |
| Missing or mismatched token | Reject the request, normally with HTTP `401` |
| Success status | Any HTTP 2xx status |
| State namespace | State is kept separately for each token |
| Value type | Every synced value is a JSON string, even when the string itself contains JSON |
| Deleted keys | Omit the key from GET responses entirely; do not return it with a `null` value |

### Push: `POST {BASE}/state`

Request:

```json
{
  "token": "your-long-random-token",
  "updates": {
    "toolbox_notes": "[{\"title\":\"Example\",\"body\":\"Hello\"}]",
    "toolbox_dark": "dark"
  },
  "deleted": [
    "toolbox_tally_items"
  ]
}
```

`updates` contains only keys changed since the last successful push. A locally deleted synced key is listed in `deleted` instead of `updates`.

The server must apply all `updates` and `deleted` entries for that token, stamp every updated key with one current server timestamp, remove every deleted key, and return JSON containing that timestamp as `at`:

```json
{
  "ok": true,
  "at": "2026-01-15T12:34:56.789Z"
}
```

The `at` field matters: after a successful push, Toolbox records it as the last-seen server timestamp for every key in that push. Extra response fields are ignored.

### Pull: `GET {BASE}/state`

Response:

```json
{
  "state": {
    "toolbox_notes": {
      "value": "[{\"title\":\"Example\",\"body\":\"Hello\"}]",
      "updated_at": "2026-01-15T12:34:56.789Z"
    },
    "toolbox_dark": {
      "value": "dark",
      "updated_at": "2026-01-15T12:30:00.000Z"
    }
  }
}
```

Each state entry must have:

- `value`: the stored string.
- `updated_at`: the server timestamp for that value.

### Timestamp rules

Toolbox compares `updated_at` values as strings, so a compatible server must use one consistent format. Use UTC ISO 8601 with exactly three fractional-second digits and a trailing `Z`:

```text
YYYY-MM-DDTHH:MM:SS.mmmZ
```

For example: `2026-01-15T12:34:56.789Z`.

Keep the server clock accurate. The reference server below also makes push timestamps move forward by at least one millisecond from its previous push timestamp, avoiding equal or backwards stamps if the clock is coarse or is adjusted.

### Conflict rules

The protocol's rules are intentionally simple:

- **On push, the server accepts the client's named changes.** It replaces the stored value for each key in `updates` and removes each key in `deleted`. There is no client timestamp or version check in a push request.
- **On pull, a locally pending key wins for now.** Toolbox skips any key with an unsynced local change; that change will be pushed afterwards.
- **Otherwise, the newer server entry wins.** Toolbox applies a server value when it has no recorded timestamp for the key or when the server's `updated_at` is greater than the device's recorded timestamp.
- **Absence means deletion.** If Toolbox has previously synced a key but the GET state no longer contains a valid string entry for it, Toolbox deletes the local value.
- **Sync Now and a sync tick pull first, then push.**

### Keys that sync

Toolbox syncs these 26 keys:

```text
toolbox_accent
toolbox_comparison_options
toolbox_shop_items
toolbox_dark
toolbox_desktop
toolbox_desktop_icon_opts
toolbox_desktop_icon_pos
toolbox_pto_logs
toolbox_pto_settings
toolbox_tally_items
toolbox_taskbar_pins
toolbox_tools_config
toolbox_tracked_times
toolbox_ui_scale
pdf_suite_preview_scale
skytrack_mobile_state
toolbox_currency_state
toolbox_time_state
toolbox_sbm_state
toolbox_split_state
toolbox_tip_state
toolbox_aviationstack_key
toolbox_gemini_key_1
toolbox_gemini_key_2
toolbox_custom_accent
toolbox_notes
```

The sync URL, sync token, sync bookkeeping (metadata and pending lists), and sync interval are per-device settings and are **not** synced. Shopping Assistant data syncs in full: both the comparison options (`toolbox_comparison_options`) and the shopping list items (`toolbox_shop_items`).

## Complete reference server (Python standard library only)

Save the following as `toolbox_sync_server.py`. It requires Python 3 and uses only the standard library.

It:

- implements `GET` and `POST` on `/state`;
- checks the `X-Toolbox-Token` header and, for POST, the body token;
- can require one exact token through `TOOLBOX_SYNC_TOKEN` (recommended);
- otherwise keeps a separate namespace for each token;
- stores namespaces under a hash of the token rather than the token itself;
- stores state as JSON in `toolbox_sync_state.json` beside the script (or at `TOOLBOX_SYNC_DATA`);
- writes the JSON file atomically;
- sends the CORS headers the browser edition needs; and
- never logs request bodies or tokens.

```python
#!/usr/bin/env python3
"""Minimal Toolbox sync server.

Uses only Python's standard library. This server speaks HTTP; put it
behind an HTTPS reverse proxy before exposing it to the internet.
"""

import argparse
import hashlib
import hmac
import json
import os
import tempfile
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ALLOWED_KEYS = {
    "toolbox_accent",
    "toolbox_comparison_options",
    "toolbox_shop_items",
    "toolbox_dark",
    "toolbox_desktop",
    "toolbox_desktop_icon_opts",
    "toolbox_desktop_icon_pos",
    "toolbox_pto_logs",
    "toolbox_pto_settings",
    "toolbox_tally_items",
    "toolbox_taskbar_pins",
    "toolbox_tools_config",
    "toolbox_tracked_times",
    "toolbox_ui_scale",
    "pdf_suite_preview_scale",
    "skytrack_mobile_state",
    "toolbox_currency_state",
    "toolbox_time_state",
    "toolbox_sbm_state",
    "toolbox_split_state",
    "toolbox_tip_state",
    "toolbox_aviationstack_key",
    "toolbox_gemini_key_1",
    "toolbox_gemini_key_2",
    "toolbox_custom_accent",
    "toolbox_notes",
}

MAX_BODY_BYTES = 50 * 1024 * 1024
EXPECTED_TOKEN = os.environ.get("TOOLBOX_SYNC_TOKEN", "")
DATA_PATH = Path(
    os.environ.get(
        "TOOLBOX_SYNC_DATA",
        str(Path(__file__).with_name("toolbox_sync_state.json")),
    )
)

_lock = threading.Lock()


def empty_database():
    return {"version": 1, "last_at_ms": 0, "namespaces": {}}


def load_database():
    if not DATA_PATH.exists():
        return empty_database()
    with DATA_PATH.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError("Sync data file must contain a JSON object")
    data.setdefault("version", 1)
    data.setdefault("last_at_ms", 0)
    data.setdefault("namespaces", {})
    return data


DATABASE = load_database()


def save_database():
    DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(
        prefix=DATA_PATH.name + ".", dir=str(DATA_PATH.parent)
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(DATABASE, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")
        os.replace(temp_name, DATA_PATH)
    except BaseException:
        try:
            os.unlink(temp_name)
        except FileNotFoundError:
            pass
        raise


def namespace_name(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def namespace_for(token, create=False):
    name = namespace_name(token)
    namespaces = DATABASE.setdefault("namespaces", {})
    if create:
        return namespaces.setdefault(name, {"state": {}})
    return namespaces.get(name, {"state": {}})


def iso_from_ms(milliseconds):
    seconds, millis = divmod(int(milliseconds), 1000)
    dt = datetime.fromtimestamp(seconds, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S") + f".{millis:03d}Z"


def next_timestamp_ms():
    now_ms = time.time_ns() // 1_000_000
    candidate = max(now_ms, int(DATABASE.get("last_at_ms", 0)) + 1)
    DATABASE["last_at_ms"] = candidate
    return candidate


def token_is_allowed(token):
    if not token:
        return False
    if EXPECTED_TOKEN:
        return hmac.compare_digest(token, EXPECTED_TOKEN)
    return True


class ToolboxSyncHandler(BaseHTTPRequestHandler):
    server_version = "ToolboxSync/1.0"

    def log_message(self, format_string, *args):
        # Keep the default request log, but never log headers or bodies.
        super().log_message(format_string, *args)

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def send_error_json(self, status, message):
        self.send_json(status, {"ok": False, "error": message})

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header(
            "Access-Control-Allow-Headers", "Content-Type, X-Toolbox-Token"
        )
        self.send_header("Access-Control-Max-Age", "86400")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def request_token(self):
        return self.headers.get("X-Toolbox-Token", "")

    def do_GET(self):
        if urlparse(self.path).path != "/state":
            self.send_error_json(404, "not found")
            return

        token = self.request_token()
        if not token_is_allowed(token):
            self.send_error_json(401, "invalid or missing token")
            return

        with _lock:
            # Return a copy so another request cannot mutate this response
            # while it is being serialized.
            state = json.loads(
                json.dumps(namespace_for(token).get("state", {}))
            )
        self.send_json(200, {"state": state})

    def do_POST(self):
        if urlparse(self.path).path != "/state":
            self.send_error_json(404, "not found")
            return

        header_token = self.request_token()
        if not token_is_allowed(header_token):
            self.send_error_json(401, "invalid or missing token")
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error_json(400, "invalid Content-Length")
            return
        if content_length < 0 or content_length > MAX_BODY_BYTES:
            self.send_error_json(413, "request body too large")
            return

        raw_body = self.rfile.read(content_length)
        try:
            body = json.loads(raw_body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self.send_error_json(400, "request body must be JSON")
            return

        if not isinstance(body, dict):
            self.send_error_json(400, "request body must be a JSON object")
            return

        body_token = body.get("token")
        if not isinstance(body_token, str) or not hmac.compare_digest(
            body_token, header_token
        ):
            self.send_error_json(401, "header and body tokens do not match")
            return

        updates = body.get("updates", {})
        deleted = body.get("deleted", [])
        if not isinstance(updates, dict) or not all(
            isinstance(k, str) and isinstance(v, str)
            for k, v in updates.items()
        ):
            self.send_error_json(
                400, "updates must be an object whose values are strings"
            )
            return
        if not isinstance(deleted, list) or not all(
            isinstance(k, str) for k in deleted
        ):
            self.send_error_json(400, "deleted must be an array of strings")
            return
        if any(k not in ALLOWED_KEYS for k in updates) or any(
            k not in ALLOWED_KEYS for k in deleted
        ):
            self.send_error_json(400, "unknown Toolbox sync key")
            return

        with _lock:
            namespace = namespace_for(header_token, create=True)
            state = namespace.setdefault("state", {})

            at = iso_from_ms(next_timestamp_ms())

            for key, value in updates.items():
                state[key] = {"value": value, "updated_at": at}
            for key in deleted:
                state.pop(key, None)

            save_database()

        self.send_json(
            200,
            {
                "ok": True,
                "at": at,
                "updated": len(updates),
                "deleted": len(deleted),
            },
        )


def main():
    parser = argparse.ArgumentParser(description="Toolbox sync server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9000)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), ToolboxSyncHandler)
    print(
        f"Toolbox sync server listening on http://{args.host}:{args.port}",
        flush=True,
    )
    print("Endpoint: /state", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
```

## Run the reference server

### 1. Choose a token

Use a long random token. Set it on the server with the `TOOLBOX_SYNC_TOKEN` environment variable so the server accepts only that token.

On Linux or macOS:

```sh
export TOOLBOX_SYNC_TOKEN='replace-with-a-long-random-token'
python3 toolbox_sync_server.py --host 0.0.0.0 --port 9000
```

On Windows PowerShell:

```powershell
$env:TOOLBOX_SYNC_TOKEN = 'replace-with-a-long-random-token'
python toolbox_sync_server.py --host 0.0.0.0 --port 9000
```

If `TOOLBOX_SYNC_TOKEN` is not set, any non-empty token is accepted and gets its own separate state namespace. That can be convenient on a trusted home network, but setting one exact token is safer.

### 2. Use it on your LAN

Find the server computer's local IP address and enter a base address like this in Toolbox:

```text
http://192.168.0.10:9000
```

Allow the chosen port through the computer's firewall if your devices cannot connect. Keep the server running while you want devices to sync.

### 3. Use it over the internet — HTTPS required

The reference server itself speaks plain HTTP. Do **not** expose the plain-HTTP port directly to the internet: the token and synced data would cross the network unencrypted.

Instead:

1. Run the Python server on a private address, such as `127.0.0.1:9000` on the same machine as your reverse proxy.
2. Put a trusted HTTPS reverse proxy (for example, Caddy or Nginx) in front of it.
3. Forward the public HTTPS origin to the Python server.
4. Enter the HTTPS origin, without `/state`, as the Sync Server URL in Toolbox.

For example, if your public sync address is `https://sync.example.com`, Toolbox will call `https://sync.example.com/state`.

If the web edition is opened from an HTTPS page, the browser may block a plain-HTTP sync server as mixed content. Use HTTPS for the sync address in that case. The CORS headers in the reference server permit the browser edition to call it from another origin, but CORS is not authentication — the token check still applies to every state request.

### 4. Keep it private and backed up

- Keep the token secret and do not reuse it elsewhere.
- Restrict access to `toolbox_sync_state.json`; it contains the synced values, potentially including API-key settings.
- Back up that JSON file if the synced data matters to you.
- To rotate the token, change `TOOLBOX_SYNC_TOKEN`, restart the server, and enter the new token on every device. The old namespace remains in the data file until you remove it manually.
