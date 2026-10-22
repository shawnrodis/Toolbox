// v1.2.0 — web edition update-checker tests.
//
// Same harness style as sync-api-keys.test.mjs: extract the real
// "Toolbox Update Checker" block from web/Toolbox.html and evaluate
// it against stubbed localStorage / document / fetch, then drive the
// REAL functions: version comparison, GitHub asset picking, the
// 24 h throttle, per-version banner dismissal, and a full check
// round trip (auto-check on load, then a failing manual check).
//
// Run: node web/test/update-checker.test.mjs   (exit code 0 = pass)

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'Toolbox.html'), 'utf8');
const BEGIN = '/* ============ Toolbox Update Checker : BEGIN ============ */';
const END = '/* ============ Toolbox Update Checker : END ============ */';
const chunk = html.slice(html.indexOf(BEGIN), html.indexOf(END) + END.length);
if (!chunk.includes('function updatePickAssets')) {
    throw new Error('update chunk extraction failed (updatePickAssets not found)');
}

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

function makeElement() {
    const classes = new Set();
    return {
        textContent: '', href: '',
        classList: {
            add: c => classes.add(c),
            remove: c => classes.delete(c),
            contains: c => classes.has(c)
        },
        _classes: classes
    };
}

function makeDocument(currentVersion) {
    const els = {};
    return {
        getElementById: id => els[id] || (els[id] = makeElement()),
        querySelector: sel => (sel === 'meta[name="version"]' ? { content: currentVersion } : null),
        _els: els
    };
}

function makeDevice({ seed = {}, current = '1.2.0', fetchImpl }) {
    const localStorage = makeLocalStorage(seed);
    const document = makeDocument(current);
    const device = { localStorage, document, calls: [] };
    const fetchStub = async (url, opts = {}) => {
        device.calls.push({ url: String(url), method: opts.method || 'GET' });
        return fetchImpl(String(url), opts, device);
    };
    const factory = new Function(
        'localStorage', 'document', 'fetch',
        chunk + '\n;return { updateCompareVersions, updateVersionParts, updateIsAvailable,' +
        ' updateShouldCheck, updatePickAssets, updateBannerVisible, updateRunCheck,' +
        ' updateCheckNow, updateDismissBanner, toolboxVersion };'
    );
    device.api = factory(localStorage, document, fetchStub);
    return device;
}

const jsonRes = (obj, status = 200) => ({
    ok: status >= 200 && status < 300, status, json: async () => obj
});

const FIXTURE = {
    tag_name: 'v9.9.9',
    html_url: 'https://github.com/shawnrodis/toolbox/releases/tag/v9.9.9',
    assets: [
        { name: 'Other_v1.0.0.apk', browser_download_url: 'https://example.com/other.apk' },
        { name: 'Toolbox_v9.9.9.apk', browser_download_url: 'https://example.com/Toolbox_v9.9.9.apk' },
        { name: 'Toolbox_v9.9.9.html', browser_download_url: 'https://example.com/Toolbox_v9.9.9.html' },
        { name: 'checksums.txt', browser_download_url: 'https://example.com/checksums.txt' }
    ]
};

// ---------------------------------------------------------------- U1
console.log('U1: version comparison');
{
    const d = makeDevice({ fetchImpl: async () => jsonRes(FIXTURE) });
    const c = d.api.updateCompareVersions;
    check('equal', c('1.2.0', '1.2.0') === 0);
    check('v-prefix ignored', c('v1.2.0', '1.2.0') === 0 && c('1.2.0', 'V1.2.0') === 0);
    check('older/newer', c('1.1.1', '1.2.0') < 0 && c('1.2.0', '1.1.1') > 0);
    check('numeric not lexicographic', c('1.10.0', '1.9.0') > 0);
    check('partial pads with zero', c('1.2', '1.2.0') === 0 && c('2', '1.9.9') > 0);
    check('prerelease suffix does not crash', c('1.2.0-beta', '1.2.0') === 0);
    check('garbage does not crash', JSON.stringify(d.api.updateVersionParts('abc')) === '[0]');
    check('current version read from meta stamp', d.api.toolboxVersion() === '1.2.0');
    check('isAvailable strictly newer only',
        d.api.updateIsAvailable('1.2.0', '1.2.1') === true &&
        d.api.updateIsAvailable('1.2.0', '1.2.0') === false &&
        d.api.updateIsAvailable('1.2.0', null) === false);
}

// ---------------------------------------------------------------- U2
console.log('U2: GitHub release parsing / asset picking');
{
    const d = makeDevice({ fetchImpl: async () => jsonRes(FIXTURE) });
    const info = d.api.updatePickAssets(FIXTURE);
    check('version stripped of v', info.version === '9.9.9');
    check('apk picked by name (not the Other_ decoy first in list)',
        info.apkUrl === 'https://example.com/Toolbox_v9.9.9.apk');
    check('html picked by name', info.htmlUrl === 'https://example.com/Toolbox_v9.9.9.html');
    check('release page from html_url', info.releaseUrl === FIXTURE.html_url);
    const bare = d.api.updatePickAssets({ tag_name: 'v1.0.1', assets: [] });
    check('no assets -> null urls', bare.apkUrl === null && bare.htmlUrl === null);
    check('missing tag -> null', d.api.updatePickAssets({ assets: [] }) === null);
}

// ---------------------------------------------------------------- U3
console.log('U3: 24 h throttle + banner dismissal rules');
{
    const d = makeDevice({ fetchImpl: async () => jsonRes(FIXTURE) });
    const DAY = 24 * 60 * 60 * 1000, now = 100 * DAY;
    check('never checked -> check', d.api.updateShouldCheck(0, now) === true);
    check('1 min ago -> skip', d.api.updateShouldCheck(now - 60000, now) === false);
    check('exactly 24 h -> check', d.api.updateShouldCheck(now - DAY, now) === true);
    const b = d.api.updateBannerVisible;
    check('banner for undismissed newer', b('1.2.0', '9.9.9', null) === true);
    check('banner hidden once that version dismissed', b('1.2.0', '9.9.9', '9.9.9') === false);
    check('older dismissal does not hide a newer version', b('1.2.0', '9.9.9', '9.9.8') === true);
    check('no banner when up to date', b('9.9.9', '9.9.9', null) === false);
}

// ---------------------------------------------------------------- U4
console.log('U4: auto-check on load populates state; manual failure is reported; dismiss hides banner');
{
    let fail = false;
    const d = makeDevice({
        fetchImpl: async () => {
            if (fail) throw new Error('network down');
            return jsonRes(FIXTURE);
        }
    });
    // updateInit() already ran during evaluation: the auto-check fires.
    await waitFor(() => d.localStorage.getItem('toolbox_update_latest_version') === '9.9.9');
    check('auto-check hit the GitHub latest-release endpoint',
        d.calls.some(c => c.url === 'https://api.github.com/repos/shawnrodis/toolbox/releases/latest'));
    check('html asset url cached', d.localStorage.getItem('toolbox_update_html_url') === 'https://example.com/Toolbox_v9.9.9.html');
    await waitFor(() => d.document._els['update-status'].textContent === 'Update available: v9.9.9');
    check('status shows the available update', true);
    check('download button points at the html asset',
        d.document._els['update-download'].href === 'https://example.com/Toolbox_v9.9.9.html');
    check('banner visible', !d.document._els['update-banner']._classes.has('hidden'));

    d.api.updateDismissBanner();
    check('dismissal recorded', d.localStorage.getItem('toolbox_update_dismissed') === '9.9.9');
    check('banner hidden after dismiss', d.document._els['update-banner']._classes.has('hidden'));

    fail = true;
    d.api.updateCheckNow(); // manual: ignores throttle, must surface the error
    await waitFor(() => d.document._els['update-status'].textContent.includes("Couldn't check"));
    check('manual check failure is reported plainly', true);
}

// ---------------------------------------------------------------- U5
console.log('U5: throttled load makes no request');
{
    const d = makeDevice({
        seed: { toolbox_update_last_check: String(Date.now()) },
        fetchImpl: async () => { throw new Error('must not be called'); }
    });
    await new Promise(r => setTimeout(r, 60));
    check('no fetch while inside the 24 h window', d.calls.length === 0);
}

console.log(failures === 0 ? '\nALL PASS' : '\n' + failures + ' FAILURE(S)');
process.exit(failures === 0 ? 0 : 1);
