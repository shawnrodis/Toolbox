// v1.2.1 — web edition long-press haptics tests.
//
// Same harness style as update-checker.test.mjs: extract the real
// "Toolbox Long-Press Haptics" block from web/Toolbox.html and drive
// it against stubbed document / navigator / timers with a manual
// clock. Covers: a held press fires exactly one tick at the file's
// 550 ms threshold; the toolbox_haptics toggle gates it; an early
// release / pointercancel / move past the 12 px slop cancels it;
// non-primary buttons never arm it; and the beep-hook de-dupe stamp
// (window.__tbLastVibrateAt) suppresses a tick landing within
// 150 ms of a beep buzz.
//
// Run: node web/test/long-press-haptics.test.mjs   (exit code 0 = pass)

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const html = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'Toolbox.html'), 'utf8');
const BEGIN = '/* ============ Toolbox Long-Press Haptics : BEGIN ============ */';
const END = '/* ============ Toolbox Long-Press Haptics : END ============ */';
const chunk = html.slice(html.indexOf(BEGIN), html.indexOf(END) + END.length);
if (!chunk.includes('initLongPressHaptics')) {
    throw new Error('long-press chunk extraction failed (initLongPressHaptics not found)');
}

let failures = 0;
function check(name, cond) {
    console.log((cond ? '  ok  ' : ' FAIL ') + name);
    if (!cond) failures++;
}

function makeClock() {
    let now = 0;
    let nextId = 1;
    const timers = new Map();
    return {
        now: () => now,
        setTimeout: (cb, ms) => { const id = nextId++; timers.set(id, { fireAt: now + ms, cb }); return id; },
        clearTimeout: (id) => { timers.delete(id); },
        advance: (ms) => {
            now += ms;
            for (;;) {
                const due = [...timers.entries()]
                    .filter(([, t]) => t.fireAt <= now)
                    .sort((a, b) => a[1].fireAt - b[1].fireAt);
                if (!due.length) break;
                const [id, t] = due[0];
                timers.delete(id);
                t.cb();
            }
        }
    };
}

function makeDevice({ haptics = 'on' } = {}) {
    const clock = makeClock();
    const store = new Map();
    if (haptics !== null) store.set('toolbox_haptics', haptics);
    const listeners = {};
    const buzzes = [];
    const documentStub = {
        addEventListener: (type, fn) => { (listeners[type] = listeners[type] || []).push(fn); }
    };
    const navigatorStub = { vibrate: (ms) => { buzzes.push(ms); } };
    const windowStub = {};
    const toolboxHapticsOn = () => store.get('toolbox_haptics') !== 'off';
    const factory = new Function(
        'document', 'navigator', 'window', 'setTimeout', 'clearTimeout', 'Date', 'toolboxHapticsOn',
        chunk
    );
    factory(
        documentStub, navigatorStub, windowStub,
        clock.setTimeout, clock.clearTimeout, { now: clock.now },
        toolboxHapticsOn
    );
    const fire = (type, ev = {}) => {
        for (const fn of listeners[type] || []) fn({ button: 0, isPrimary: true, clientX: 0, clientY: 0, ...ev });
    };
    return { clock, store, buzzes, windowStub, fire };
}

// ---------------------------------------------------------------- L1
console.log('L1: held press fires exactly one tick at 550 ms');
{
    const d = makeDevice();
    d.fire('pointerdown');
    d.clock.advance(549);
    check('nothing before the threshold', d.buzzes.length === 0);
    d.clock.advance(1);
    check('one 10 ms tick at 550 ms', d.buzzes.length === 1 && d.buzzes[0] === 10);
    d.clock.advance(2000);
    check('no repeat while still held', d.buzzes.length === 1);
}

// ---------------------------------------------------------------- L2
console.log('L2: the toolbox_haptics toggle gates the tick');
{
    const d = makeDevice({ haptics: 'off' });
    d.fire('pointerdown');
    d.clock.advance(1500);
    check('toggle off -> no buzz', d.buzzes.length === 0);
    const d2 = makeDevice({ haptics: null }); // absent = on (default)
    d2.fire('pointerdown');
    d2.clock.advance(550);
    check('toggle absent (default on) -> buzz', d2.buzzes.length === 1);
}

// ---------------------------------------------------------------- L3
console.log('L3: early release / cancel / slop movement cancels');
{
    const up = makeDevice();
    up.fire('pointerdown');
    up.clock.advance(300);
    up.fire('pointerup');
    up.clock.advance(1000);
    check('pointerup at 300 ms cancels', up.buzzes.length === 0);

    const cancel = makeDevice();
    cancel.fire('pointerdown');
    cancel.clock.advance(300);
    cancel.fire('pointercancel');
    cancel.clock.advance(1000);
    check('pointercancel cancels', cancel.buzzes.length === 0);

    const drag = makeDevice();
    drag.fire('pointerdown', { clientX: 100, clientY: 100 });
    drag.clock.advance(200);
    drag.fire('pointermove', { clientX: 125, clientY: 100 }); // 25 px > 12 px slop
    drag.clock.advance(1000);
    check('move past slop cancels (drags/scrolls stay silent)', drag.buzzes.length === 0);

    const wiggle = makeDevice();
    wiggle.fire('pointerdown', { clientX: 100, clientY: 100 });
    wiggle.clock.advance(200);
    wiggle.fire('pointermove', { clientX: 105, clientY: 103 }); // ~5.8 px < slop
    wiggle.clock.advance(350);
    check('small wiggle inside slop still fires', wiggle.buzzes.length === 1);
}

// ---------------------------------------------------------------- L4
console.log('L4: non-primary buttons never arm the detector');
{
    const d = makeDevice();
    d.fire('pointerdown', { button: 2 }); // right-click: desktop context-menu path
    d.clock.advance(1500);
    check('right button -> no buzz', d.buzzes.length === 0);
    const d2 = makeDevice();
    d2.fire('pointerdown', { isPrimary: false }); // second finger
    d2.clock.advance(1500);
    check('non-primary pointer -> no buzz', d2.buzzes.length === 0);
}

// ---------------------------------------------------------------- L5
console.log('L5: beep-hook de-dupe stamp');
{
    const d = makeDevice();
    d.fire('pointerdown');
    d.clock.advance(500);
    d.windowStub.__tbLastVibrateAt = d.clock.now(); // a beep buzzed just now
    d.clock.advance(50); // long-press timer fires at 550, 50 ms after the stamp
    check('tick within 150 ms of a beep buzz is dropped', d.buzzes.length === 0);
    d.fire('pointerup');
    d.fire('pointerdown');
    d.clock.advance(550); // stamp is now 600 ms old
    check('next held press buzzes normally', d.buzzes.length === 1);
}

// ---------------------------------------------------------------- L6
console.log('L6: back-to-back held presses each buzz once');
{
    const d = makeDevice();
    d.fire('pointerdown');
    d.clock.advance(550);
    d.fire('pointerup');
    d.fire('pointerdown');
    d.clock.advance(550);
    check('two held presses -> two ticks', d.buzzes.length === 2);
}

console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
