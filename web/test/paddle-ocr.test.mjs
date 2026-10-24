// v1.3.0 — web edition PDF Tools local OCR is PaddleOCR (ppu-paddle-ocr
// 6.6.1 + onnxruntime-web 1.26.0, PP-OCRv6 tiny models), replacing
// Tesseract.js. No browser needed: this harness
//   1. structurally checks web/Toolbox.html (no Tesseract left, pinned
//      import map, paddle selector wiring, version stamps);
//   2. syntax-checks every inline classic script in the file;
//   3. extracts the REAL sanitizeText / loadPaddleOcrService /
//      processSearchableOCR chunk and drives it against stubbed
//      PDFLib / pdf.js / document plus a stub PaddleOcrService module
//      (the chunk's pinned CDN import URL is swapped for the stub at
//      extraction time), verifying: the service is built once with the
//      pinned model URLs and reused; per-line results are drawn once
//      per line at the union box with font size from line height; a
//      failed first load is retried on the next run; and the Gemini
//      ("ai") branch still draws its text exactly as before.
//
// Run: node web/test/paddle-ocr.test.mjs   (exit code 0 = all pass)

import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';
import vm from 'node:vm';

const htmlPath = join(dirname(fileURLToPath(import.meta.url)), '..', 'Toolbox.html');
const html = readFileSync(htmlPath, 'utf8');

let failures = 0;
function check(name, cond) {
    console.log((cond ? '  ok  ' : ' FAIL ') + name);
    if (!cond) failures++;
}

// ---------- 1. structural checks ----------

check('no Tesseract references remain', !/tesseract/i.test(html));
check('no ML Kit references', !/mlkit|ML Kit/i.test(html));

const importmapMatch = html.match(/<script type="importmap">([\s\S]*?)<\/script>/);
check('import map present', !!importmapMatch);
let importmap = {};
try { importmap = JSON.parse(importmapMatch[1]); } catch (e) { /* failing parse flagged below */ }
check(
    'import map pins onnxruntime-web 1.26.0 bundle',
    importmap.imports?.['onnxruntime-web'] ===
        'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.26.0/dist/ort.all.bundle.min.mjs'
);
check(
    'import map pins ppu-ocv canvas-web 4.0.0',
    importmap.imports?.['ppu-ocv/canvas-web'] ===
        'https://cdn.jsdelivr.net/npm/ppu-ocv@4.0.0/index.canvas-web.js'
);
check(
    'selector button is pdf-btn-ocr-paddle wired to pdfSetOcrEngine(\'paddle\')',
    html.includes('id="pdf-btn-ocr-paddle"') && html.includes("pdfSetOcrEngine('paddle')")
);
check('sub-label reads PaddleOCR (Offline)', html.includes('PaddleOCR (Offline)'));
check('default engine state is paddle', html.includes("let ocrEngine = 'paddle';"));
check('version stamps at 1.3.0', html.includes('<meta name="version" content="1.3.0">') &&
    html.includes('Web Edition v1.3.0') && html.includes('Version 1.3.0 · by Shawn Rodis'));

// ---------- 2. syntax-check every inline classic script ----------

const scriptBlocks = [...html.matchAll(/<script(?![^>]*\bsrc=)([^>]*)>([\s\S]*?)<\/script>/g)];
let syntaxChecked = 0;
for (const [, attrs, code] of scriptBlocks) {
    if (attrs.includes('importmap')) continue;
    try {
        new vm.Script(code);
        syntaxChecked++;
    } catch (e) {
        check('inline script syntax (' + e.message.slice(0, 60) + '...)', false);
    }
}
check(`all ${syntaxChecked} inline classic scripts parse`, syntaxChecked > 0);

// ---------- 3. drive the real OCR chunk with stubs ----------

const BEGIN = 'function sanitizeText';
const END = 'function downloadBlob';
const chunk = html.slice(html.indexOf(BEGIN), html.indexOf(END));
check('chunk extraction found sanitizeText + processSearchableOCR',
    chunk.includes('function sanitizeText') && chunk.includes('async function processSearchableOCR()'));
check('chunk contains the pinned ppu-paddle-ocr module URL',
    chunk.includes("await import('https://cdn.jsdelivr.net/npm/ppu-paddle-ocr@6.6.1/web/index.js')"));
check('chunk pins the PP-OCRv6 tiny .onnx models + dictionary',
    chunk.includes('detection/PP-OCRv6_tiny_det.onnx') &&
    chunk.includes('recognition/PP-OCRv6_tiny_rec.onnx') &&
    chunk.includes('recognition/ppocrv6_tiny_dict.txt'));

const work = mkdtempSync(join(tmpdir(), 'paddle-ocr-harness-'));
const stubPath = join(work, 'paddle-stub.mjs');
writeFileSync(stubPath, `
export class PaddleOcrService {
    static instances = [];
    constructor(config) { this.config = config; PaddleOcrService.instances.push(this); }
    async initialize() {
        if (globalThis.__paddleFailNextInit) {
            globalThis.__paddleFailNextInit = false;
            throw new Error('init boom');
        }
    }
    async recognize(canvas) {
        return {
            text: 'Hello world\\nSecond line', confidence: 0.97,
            lines: [
                [ { text: 'Hello', box: { x: 10, y: 20, width: 100, height: 20 }, confidence: 0.98 },
                  { text: 'world', box: { x: 120, y: 22, width: 80, height: 18 }, confidence: 0.97 } ],
                [ { text: 'Second line', box: { x: 10, y: 60, width: 150, height: 22 }, confidence: 0.96 } ]
            ]
        };
    }
}
`);

const stubs = `
globalThis.__drawCalls = [];
let uploadedFiles = [{ type: 'application/pdf', arrayBuffer: async () => new ArrayBuffer(8) }];
let ocrEngine = 'paddle';
async function callAiVisionOcrWithFailover(b64) { return 'Alpha\\nBeta'; }
async function blobToBase64(blob) { return 'AAAA'; }
const __fakePage = {
    getSize: () => ({ width: 612, height: 792 }),
    drawText: (text, opts) => { globalThis.__drawCalls.push({ text, ...opts }); }
};
const PDFLib = {
    StandardFonts: { Helvetica: 'Helvetica' },
    PDFDocument: {
        create: async () => ({
            embedFont: async () => ({}),
            copyPages: async (src, idx) => [__fakePage],
            addPage: () => {},
            save: async () => new Uint8Array([37, 80, 68, 70])
        }),
        load: async () => ({ getPageIndices: () => [0] })
    }
};
const pdfjsLib = {
    getDocument: () => ({ promise: Promise.resolve({
        getPage: async () => ({
            getViewport: () => ({ width: 1224, height: 1584 }),
            render: () => ({ promise: Promise.resolve() })
        })
    }) })
};
const document = {
    createElement: () => ({ width: 0, height: 0, getContext: () => ({}), toBlob: (cb) => cb({}) }),
    getElementById: () => ({ innerHTML: '' })
};
`;

const chunkPath = join(work, 'chunk.mjs');
const chunkCode = stubs + chunk.replace(
    'https://cdn.jsdelivr.net/npm/ppu-paddle-ocr@6.6.1/web/index.js',
    pathToFileURL(stubPath).href
) + '\nexport { processSearchableOCR };\nexport function __setEngine(e) { ocrEngine = e; }\n';
writeFileSync(chunkPath, chunkCode);

const mod = await import(pathToFileURL(chunkPath).href);
const stub = await import(pathToFileURL(stubPath).href);

// Paddle run 1: line-level drawing at union boxes (page 612x792, viewport x2).
await mod.processSearchableOCR();
let calls = globalThis.__drawCalls;
check('paddle run draws one invisible entry per line', calls.length === 2);
check('line 1 joins segments with a space, drawn at the union box',
    calls[0]?.text === 'Hello world' && calls[0].x === 5 && calls[0].y === 772 &&
    calls[0].size === 10 && calls[0].opacity === 0);
check('line 2 drawn at its box with font size from line height',
    calls[1]?.text === 'Second line' && calls[1].x === 5 && calls[1].y === 751 &&
    calls[1].size === 11 && calls[1].opacity === 0);
check('service constructed once, with the pinned HF model config',
    stub.PaddleOcrService.instances.length === 1 &&
    stub.PaddleOcrService.instances[0].config.model.detection.endsWith('detection/PP-OCRv6_tiny_det.onnx') &&
    stub.PaddleOcrService.instances[0].config.model.recognition.endsWith('recognition/PP-OCRv6_tiny_rec.onnx') &&
    stub.PaddleOcrService.instances[0].config.model.charactersDictionary.endsWith('recognition/ppocrv6_tiny_dict.txt'));

// Paddle run 2: the cached service is reused (no second construction).
globalThis.__drawCalls = [];
await mod.processSearchableOCR();
check('second run reuses the cached service', stub.PaddleOcrService.instances.length === 1 &&
    globalThis.__drawCalls.length === 2);

// Gemini branch: unchanged layout (x=40 flow, sizes clamped to 12).
mod.__setEngine('ai');
globalThis.__drawCalls = [];
await mod.processSearchableOCR();
calls = globalThis.__drawCalls;
check('ai branch draws returned lines in the fixed flow layout',
    calls.length === 2 &&
    calls[0].text === 'Alpha' && calls[0].x === 40 && calls[0].y === 752 && calls[0].size === 12 &&
    calls[1].text === 'Beta' && calls[1].x === 40 && calls[1].y === 396 && calls[1].size === 12);

// Failure reset: a fresh chunk module (its own cached-promise slot)
// whose first initialize() throws must reject that run, then recover.
const mod2 = await import(pathToFileURL(chunkPath).href + '?fresh=1');
const before = stub.PaddleOcrService.instances.length;
globalThis.__paddleFailNextInit = true;
let rejected = false;
try { await mod2.processSearchableOCR(); } catch (e) { rejected = e.message === 'init boom'; }
check('failed first load rejects the run', rejected);
globalThis.__drawCalls = [];
await mod2.processSearchableOCR();
check('next run retries the load and succeeds',
    stub.PaddleOcrService.instances.length === before + 2 && globalThis.__drawCalls.length === 2);

console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`);
process.exit(failures === 0 ? 0 : 1);
