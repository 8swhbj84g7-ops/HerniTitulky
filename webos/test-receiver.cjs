const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const parse = require('./service/protocol').parse;
const packet = text => parse(Buffer.from(text));

for (const text of [
    'PCO1\t1\t0.1\t0.2\t0.1\t0.8', 'PCO1\t2\t0\t0\t1\t1',
    'PCO1\t1\tNaN\t0\t1\t1', 'PCO1\t1\t\t0\t1\t1',
    'PCV1\tx\t2\t0\t0\t1\t1\t0\ttext', 'PCV1\t1\t3\t0\t0\t1\t1\t0\ttext',
    'PCV1\t1\t0\t0\t0\t1\t1\t-3\ttext', 'PCV1\t1\t0\t0\t0\t1\t1\t0\t' + 'x'.repeat(4097),
    'PCV1\t1\t0\t0\t0\t1\t1\t0\t' + Array(65).fill('a').join('\\n')
]) assert.equal(packet(text), null, text.slice(0, 80));
assert.deepEqual(packet('PCO1\t1\t-1\t0\t2\t1').area, [0, 0, 1, 1]);
assert.equal(packet('PCV1\t9223372036854775807\t0\t0\t0\t1\t1\t-2\tDopis').id, '9223372036854775807');
assert.equal(packet('PCV1\t1\t0\t0\t0\t1\t1\t99\tJedna').selected, -1);
assert.deepEqual(packet('PCV1\t1\t0\t0\t0\t1\t1\t0\tCesta \\\\n\\nDruhá').items, ['Cesta \\n', 'Druhá']);

class Element {
    constructor() { this.style = {}; this.hidden = true; this.children = []; this.value = ''; this.scrollHeight = 200; }
    set textContent(text) { this.value = String(text); this.children = []; }
    get textContent() { return this.value + this.children.map(c => c.textContent).join(''); }
    appendChild(child) { this.children.push(child); }
}
const elements = Object.fromEntries(['status', 'subtitle', 'hint', 'choices', 'choice-frame', 'ocr-frame'].map(id => [id, new Element()]));
const document = {
    documentElement: { clientHeight: 1080 }, getElementById: id => elements[id],
    createElement: () => new Element(), createTextNode: text => ({ textContent: text }), addEventListener() {}
};
const window = { addEventListener() {}, close() {} };
vm.runInNewContext(fs.readFileSync(__dirname + '/app/receiver-ui.js', 'utf8'), { window });
const ui = new window.PlayCaptionReceiverUI(document);
const choice = (id, frame, selected = 1, text = 'První volba\\nDruhá volba') =>
    packet(`PCV1\t${id}\t${frame}\t0.55\t0.45\t0.95\t0.8\t${selected}\t${text}`);
ui.handle(choice(1, 2), 1000);
assert.equal(elements['choice-frame'].hidden, false);
assert.ok(Math.abs(parseFloat(elements['choice-frame'].style.left) - 55) < 0.001);
assert.equal(elements.choices.children[1].className, 'choice selected');
assert.ok(Math.abs(parseFloat(elements.choices.style.left) - 12) < 0.001);
assert.equal(elements.choices.textContent, 'První volbaDruhá volba');
ui.tick(100000);
assert.equal(elements['choice-frame'].hidden, false, 'Trvalý rámeček neexpiruje při živém spojení.');
ui.handle(choice(1, 0), 100000);
assert.equal(elements['choice-frame'].hidden, true, 'Změna režimu rámečku platí i při stejném ID.');
ui.tick(145001);
ui.handle(choice(1, 0), 145002);
assert.equal(elements.choices.hidden, true, 'Heartbeat neoživí prošlé volby.');
ui.handle(choice(2, 0, -2, 'Celý odstavec instrukce.'), 150000);
ui.tick(196000);
assert.equal(elements.choices.hidden, false, 'Text má delší čas čtení než volby.');
ui.tick(450001);
assert.equal(elements.choices.hidden, true);
ui.handle(choice(3, 1, -1, ''), 500000);
assert.equal(elements['choice-frame'].hidden, false, 'Rámeček funguje i bez přečteného textu.');
assert.equal(elements.choices.hidden, true);
ui.handle(choice(3, 1, -1, ''), 507000);
ui.tick(508001);
assert.equal(elements['choice-frame'].hidden, true, 'Dočasný rámeček nemá heartbeat prodlužovat.');
ui.handle(packet('PCO1\t1\t0.1\t0.72\t0.9\t0.97'), 600000);
assert.equal(elements['ocr-frame'].hidden, false);
assert.equal(elements['ocr-frame'].style.top, '72%');
ui.handle(packet('PCO1\t0\t0.1\t0.72\t0.9\t0.97'), 600100);
assert.equal(elements['ocr-frame'].hidden, true);
ui.handle(packet('PCO1\t1\t0.1\t0.72\t0.9\t0.97'), 601000);
ui.tick(609001);
assert.equal(elements['ocr-frame'].hidden, true);
ui.caption(packet('PCT1\t1\t6000\tLucia: <script>\\nJason: Ahoj\t\t0.5\t0.9\t0.8\t1\t1\tB:-16711936,-65536'), 610000);
assert.equal(elements.subtitle.textContent, 'Lucia: <script>\nJason: Ahoj');
assert.equal(elements.subtitle.children[0].style.color, 'rgba(0,255,0,1)');
assert.equal(elements.subtitle.children[3].style.color, 'rgba(255,0,0,1)');
assert.match(elements.subtitle.style.outline, /dashed/);
ui.tick(618001);
assert.equal(elements.subtitle.style.outline, '');
ui.clear();
assert.equal(elements.choices.hidden, true);
const leftChoices = packet('PCV1\t4\t0\t0.05\t0.4\t0.45\t0.8\t0\tOdpověď vpravo');
ui.handle(leftChoices, 620000);
assert.ok(parseFloat(elements.choices.style.left) > 45, 'Oblast vlevo má překlad vpravo.');
assert.equal(elements.choices.children[0].className, 'choice selected');
elements.choices.scrollHeight = 900;
ui.handle(packet('PCV1\t5\t0\t0.1\t0.9\t0.9\t1\t-2\tDlouhý text'), 621000);
assert.ok(parseFloat(elements.choices.style.top) < 20, 'Dlouhý text u dolního okraje se posune do obrazovky.');
assert.equal(elements.choices.style.maxHeight, '90vh');
assert.equal(packet('PCT1\t1\t6000\tJméno: text\t\tB:9999999999,NaN').colors[0], 0);
assert.equal(packet('PCT1\t1\t6000\tJméno: text\t\tB:9999999999,NaN').colors[1], 0);

// Celá UI cesta: samostatné kanály se stejným ID a společný hlídač spojení.
let receive, tick, now = 700000;
function Bridge() {
    this.call = uri => { if (uri.endsWith('/listen')) receive = this.onservicecallback; };
    this.cancel = () => {};
}
vm.runInNewContext(fs.readFileSync(__dirname + '/app/app.js', 'utf8'), {
    window, document, PalmServiceBridge: Bridge, Date: { now: () => now },
    setInterval: fn => { tick = fn; return 1; }, clearInterval() {}
});
const send = p => receive(JSON.stringify({ returnValue: true, source: '192.168.0.2', packet: p }));
send(packet('PCT1\t1\t6000\tDialog\t'));
send(choice(1, 2));
send(packet('PCO1\t1\t0.1\t0.72\t0.9\t0.97'));
assert.equal(elements.subtitle.textContent, 'Dialog');
assert.equal(elements.choices.hidden, false);
assert.equal(elements['choice-frame'].hidden, false);
assert.equal(elements['ocr-frame'].hidden, false);
now += 5100; tick();
for (const id of ['subtitle', 'choices', 'choice-frame', 'ocr-frame']) assert.equal(elements[id].hidden, true, id);
send(choice(1, 2));
assert.equal(elements.choices.hidden, false, 'Po obnovení spojení funguje i stejné ID.');
console.log('OK: PCV1/PCO1, rámečky, umístění voleb, dlouhý text, barvy jmen, heartbeat a výpadek spojení.');
