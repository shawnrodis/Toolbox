// v1.1.1 — web edition sync regression tests for API-key propagation.
//
// No browser needed: this harness extracts the sync section of
// web/Toolbox.html (from the "Toolbox Self-Hosted Sync : BEGIN" marker
// to just before the Settings pre-fill block) and evaluates it against
// stubbed localStorage / document / fetch, then drives the REAL sync
// functions through the scenarios Shawn's requirement is about:
//
//   S1  keys entered BEFORE sync was enabled still push on first sync
//       (server provider, via the real Settings save path);
//   S2  a key saved through the Settings saver is trimmed, pushes as an
//       update, and clearing it pushes a deletion that removes the key
//       on a receiving device (server provider);
//   S3  the same first-sync seeding works over the Firebase transport.
//
// Run: node web/test/sync-api-keys.test.mjs   (exit code 0 = all pass)

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'Toolbox.html'), 'utf8');
const BEGIN = '/* ============ Toolbox Self-Hosted Sync : BEGIN ============ */';
const END = '// Pre-fill the Settings inputs; kick off the silent startup sync.';
const chunk = html.slice(html.indexOf(BEGIN), html.indexOf(END));
if (!chunk.includes('function syncSeedPending')) {
    throw new Error('sync chunk extraction failed (syncSeedPending not found)');
}

const AV = 'toolbox_aviationstack_key';
const G1 = 'toolbox_gemini_key_1';
const G2 = 'toolbox_gemini_key_2';

let failures = 0;
function check(name, cond) {
    console.log((cond ? '  ok  ' : ' FAIL ') + name);
    if (!cond) failures++;
}
async function waitFor(cond, ms = 5000) {
    const t0 = Date.now();
    while (!cond()) {
        if (Date.now() - t0 > ms) throw new Error('waitFor timeout');
        await new Promise(r => setTimeout(r, 10));
    }
}

function makeLocalStorage(seed) {
    const map = new Map(Object.entries(seed || {}));
    return {
        getItem: k => (map.has(k) ? map.get(k) : null),
        setItem: (k, v) => { map.set(k, String(v)); },
        removeItem: k => { map.delete(k); },
        _map: map
    };
}

function makeDocument(fields) {
    const els = {};
    for (const [id, value] of Object.entries(fields || {})) els[id] = { value, textContent: '' };
    return { getElementById: id => els[id] || null, addEventListener: () => {}, hidden: false, _els: els };
}

// A fresh "device": evaluate the sync chunk against fresh stubs.
// fetchImpl(url, opts, device) must return a Response-like object.
function makeDevice({ seed = {}, fields = {}, fetchImpl }) {
    const localStorage = makeLocalStorage(seed);
    const document = makeDocument(fields);
    const device = { localStorage, document, calls: [] };
    const fetchStub = async (url, opts = {}) => {
        device.calls.push({ url: String(url), method: opts.method || 'GET', body: opts.body || null });
        return fetchImpl(String(url), opts, device);
    };
    const factory = new Function(
        'localStorage', 'document', 'location', 'showToast', 'fetch',
        chunk + '\n;return { SYNC_KEYS, syncReadPending, syncSeedPending, syncPushNow, syncPullNow,' +
        ' syncSaveSettings, saveAviationstackKey, saveGeminiKeys, fbPushNow, fbPullNow,' +
        ' syncConfigured, syncTick };'
    );
    device.api = factory(localStorage, document, { reload: () => {} }, () => {}, fetchStub);
    return device;
}

const jsonRes = (obj, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => obj });

// A fake self-hosted sync server holding a state map.
function fakeServer() {
    const state = {};
    const impl = async (url, opts) => {
        if ((opts.method || 'GET') === 'POST') {
            const body = JSON.parse(opts.body);
            const at = new Date().toISOString();
            for (const [k, v] of Object.entries(body.updates || {})) state[k] = { value: v, updated_at: at };
            for (const k of body.deleted || []) delete state[k];
            return jsonRes({ at });
        }
        return jsonRes({ state });
    };
    return { state, impl };
}

// ---------------------------------------------------------------- S1
console.log('S1: pre-existing keys push on first sync (server, via Settings save)');
{
    const server = fakeServer();
    const device = makeDevice({
        // Keys typed BEFORE sync was ever configured (no meta, no pending).
        seed: { [AV]: 'AV-KEY-1', [G1]: 'GEM-KEY-1', [G2]: 'GEM-KEY-2' },
        fields: { 'sync-url': 'https://sync.example.com/', 'sync-token': 'TOK' },
        fetchImpl: server.impl
    });
    device.api.syncSaveSettings(); // wipes meta+pending, then startup tick
    await waitFor(() => device.calls.some(c => c.method === 'POST'));
    const post = device.calls.find(c => c.method === 'POST');
    const body = JSON.parse(post.body);
    check('push carries the pre-existing Aviationstack key', body.updates[AV] === 'AV-KEY-1');
    check('push carries the pre-existing Gemini key 1', body.updates[G1] === 'GEM-KEY-1');
    check('push carries the pre-existing Gemini key 2', body.updates[G2] === 'GEM-KEY-2');
    check('nothing deleted', (body.deleted || []).length === 0);
    check('pending drained after push', Object.keys(device.api.syncReadPending()).length === 0);
}

// ---------------------------------------------------------------- S2
console.log('S2: save trims + pushes; clear pushes a deletion; receiver deletes (server)');
{
    const server = fakeServer();
    const a = makeDevice({
        seed: { toolbox_sync_url: 'https://sync.example.com', toolbox_sync_token: 'TOK' },
        fetchImpl: server.impl
    });
    a.api.saveAviationstackKey('  AV-KEY-9  ');
    check('saver trims the stored value', a.localStorage.getItem(AV) === 'AV-KEY-9');
    await a.api.syncPushNow();
    check('server received the key', server.state[AV] && server.state[AV].value === 'AV-KEY-9');

    a.api.saveAviationstackKey('');
    check('clearing removes the key locally', a.localStorage.getItem(AV) === null);
    await a.api.syncPushNow();
    check('clearing deleted it on the server', !(AV in server.state));

    // Device B still holds the key + meta from when it was synced.
    const b = makeDevice({
        seed: {
            toolbox_sync_url: 'https://sync.example.com', toolbox_sync_token: 'TOK',
            [AV]: 'AV-KEY-9', toolbox_sync_meta: JSON.stringify({ [AV]: '2026-10-22T00:00:00.000Z' })
        },
        fetchImpl: server.impl
    });
    const pull = await b.api.syncPullNow();
    check('receiver pull reports the deletion', pull.ok && pull.applied >= 1);
    check('receiver key is gone', b.localStorage.getItem(AV) === null);
}

// ---------------------------------------------------------------- S3
console.log('S3: pre-existing keys push on first sync (Firebase transport)');
{
    const cfg = JSON.stringify({
        apiKey: 'TEST-API-KEY', databaseURL: 'https://fake-db.example.com', projectId: 'fake'
    });
    let patchBody = null;
    const device = makeDevice({
        seed: {
            toolbox_sync_provider: 'firebase',
            toolbox_sync_fb_config: cfg,
            toolbox_sync_fb_refresh: 'RT-1',
            toolbox_sync_fb_uid: 'UID-1',
            [G1]: 'GEM-KEY-1' // entered before Firebase was ever signed in
        },
        fetchImpl: async (url, opts) => {
            if (url.includes('securetoken.googleapis.com')) {
                return jsonRes({ id_token: 'IDT', refresh_token: 'RT-2', user_id: 'UID-1', expires_in: '3600' });
            }
            if (url.includes('fake-db.example.com')) {
                if ((opts.method || 'GET') === 'PATCH') { patchBody = JSON.parse(opts.body); return jsonRes({}); }
                return jsonRes(null); // empty database
            }
            throw new Error('unexpected fetch: ' + url);
        }
    });
    const res = await device.api.fbPushNow();
    check('firebase push ok', res.ok === true);
    check('PATCH body carries the pre-existing Gemini key',
        !!patchBody && patchBody[G1] && patchBody[G1].value === 'GEM-KEY-1' &&
        typeof patchBody[G1].updated_at === 'string');
    check('pending drained after firebase push', Object.keys(device.api.syncReadPending()).length === 0);
}

console.log(failures === 0 ? '\nALL PASS' : '\n' + failures + ' FAILURE(S)');
process.exit(failures === 0 ? 0 : 1);
