// v1.3.16 — Flight Tracker recents: capped at 3, with a Clear button that
// stays reachable. No browser needed: this harness
//   1. extracts the REAL skyAddSearchHistory chunk from web/Toolbox.html
//      and drives it: empty list, prepend, cap-at-3 eviction, duplicate
//      re-search leaving the order untouched;
//   2. structurally checks both editions: no 5-cap remains in either
//      history path, the web load trims legacy histories to 3, and the
//      Android Recent row scrolls horizontally instead of squeezing chips.
//
// Run: node web/test/flight-recents.test.mjs   (exit code 0 = all pass)

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import vm from 'node:vm';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const html = readFileSync(join(root, 'web', 'Toolbox.html'), 'utf8');
const kt = readFileSync(join(root, 'app/src/main/java/com/shawnrodis/toolbox/tools/FlightTrackerScreen.kt'), 'utf8');

let failures = 0;
function check(name, cond) {
    console.log((cond ? '  ok  ' : ' FAIL ') + name);
    if (!cond) failures++;
}

// ---------- 1. the real web helper, driven ----------
const fnMatch = html.match(/function skyAddSearchHistory\(history, code\) \{[\s\S]*?\n        \}/);
check('skyAddSearchHistory present in Toolbox.html', !!fnMatch);
const ctx = {};
vm.createContext(ctx);
vm.runInContext(fnMatch[0], ctx);
const add = ctx.skyAddSearchHistory;

check('empty + code -> [code]',
    JSON.stringify(add([], 'PT5812')) === JSON.stringify(['PT5812']));
check('prepends a new code',
    JSON.stringify(add(['PT5812'], 'QR110')) === JSON.stringify(['QR110', 'PT5812']));
check('caps at 3, oldest drops off',
    JSON.stringify(add(['QR110', 'PT5812', 'QR928'], 'QR932')) === JSON.stringify(['QR932', 'QR110', 'PT5812']));
check('duplicate re-search leaves order untouched',
    JSON.stringify(add(['QR110', 'PT5812'], 'PT5812')) === JSON.stringify(['QR110', 'PT5812']));

// ---------- 2. structural checks, both editions ----------
check('no 5-cap remains in web history paths', !/length > 5/.test(html) && !/take\(5\)/.test(html));
check('web load trims legacy histories to 3',
    html.includes('parsed.searchHistory.slice(0, 3)'));
check('web Clear button still wired', html.includes("getElementById('sky-clear-recents-btn').addEventListener('click', skyClearRecents)"));
check('no 5-cap remains in Android history paths', !/take\(5\)/.test(kt));
check('Android helper caps at 3', /\.take\(3\)/.test(kt) && kt.includes('fun skyAddSearchHistory'));
check('Android load trims legacy histories to 3', kt.includes('.filter { it.isNotEmpty() }.take(3)'));
check('Android Recent row scrolls horizontally',
    kt.includes('horizontalScroll(rememberScrollState())'));
check('Android Clear button still present', kt.includes('}) { Text("Clear") }'));

console.log(failures === 0 ? 'ALL PASS' : `${failures} FAILURES`);
process.exit(failures === 0 ? 0 : 1);
