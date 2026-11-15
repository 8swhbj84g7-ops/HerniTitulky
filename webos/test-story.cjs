const assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const parse = require('./service/protocol').parse;
class Element {
    constructor() { this.style={};this.hidden=true;this.children=[];this.value=''; }
    set textContent(t) { this.value=String(t);this.children=[]; }
    get textContent() { return this.value+this.children.map(c=>c.textContent).join(''); }
    appendChild(c) { this.children.push(c); }
}
const elements=Object.fromEntries(['subtitle','choices','choice-frame','ocr-frame'].map(k=>[k,new Element()]));
const document={getElementById:id=>elements[id],createElement:()=>new Element(),createTextNode:t=>({textContent:t})};
const window={};vm.runInNewContext(fs.readFileSync(__dirname+'/app/receiver-ui.js','utf8'),{window});
const ui=new window.PlayCaptionReceiverUI(document), sub=elements.subtitle;
Object.defineProperty(sub,'clientHeight',{get:()=>40});
Object.defineProperty(sub,'scrollHeight',{get:()=>Math.ceil(Array.from(sub.textContent).length/22)*20});
const root='a'.repeat(32);
function packet(n,text,pause=false,g=1){return parse(Buffer.from(`PCS1\t${root}.${g}.${n}.0\t${pause?1:0}\t${text}\t0.5\t0.91\t0.8\t1\t-65536`));}
assert.equal(parse(Buffer.from(`PCQ1\t${root}.1.0.0`)).kind,'storyQuery');
for(const p of ['PCQ1\tx','PCS1\tx\t0\ttext',`PCQ1\t${root}.1.0.0\textra`])assert.equal(parse(Buffer.from(p)),null);
const full='Trevor: Musíme najít cestu domů. Potom se vrátíme pro ostatní. Až bude klid, zavolej mi.';
const p=packet(1,full);assert.equal(ui.story(p,0),'reading');
let now=0;const pages=[];
while(!sub.hidden){
 const text=sub.textContent;pages.push(text);
 const ms=Math.max(1800,Math.ceil(Array.from(text.normalize('NFC')).length*1000/14));
 assert.equal(ui.story(p,now+ms-1),'reading');ui.tick(now+ms-1);assert.equal(sub.textContent,text);
 now+=ms;ui.tick(now);
 assert.ok(pages.length<30);
}
assert.equal(pages.join(' ').replace(/\s+/g,' ').trim(),full);
assert.equal(ui.story(p,now+1000),'done','Ztracené ACK musí jít zopakovat bez restartu čtení.');
assert.equal(sub.hidden,true);
const q=packet(2,'Počkej.');ui.story(q,now+2000);ui.tick(now+2500);
ui.story(packet(2,'Počkej.',true),now+2500);assert.equal(sub.hidden,true);
ui.tick(now+20000);ui.story(q,now+20000);assert.equal(sub.hidden,false);
ui.tick(now+21299);assert.equal(sub.hidden,false);ui.tick(now+21300);assert.equal(sub.hidden,true);
ui.story(packet(3,'Poslední.'),now+22000);ui.pauseStory(now+22500);
ui.story(packet(3,'Poslední.'),now+30000);ui.tick(now+31299);assert.equal(sub.hidden,false);
ui.tick(now+31300);assert.equal(sub.hidden,true);
ui.story(packet(0,'',true,2),now+32000);assert.equal(ui.storyActive(),false);
assert.equal(ui.oldStory(p.id),true,'Starý paket po přepnutí do Standardního režimu nesmí obnovit titulek.');
ui.caption(parse(Buffer.from('PCT1\t10\t6000\tStandardní titulek\t')),now+32001);
assert.equal(sub.textContent,'Standardní titulek');
ui.story(packet(1,'Nedočtené.',false,3),now+33000);ui.clear();
assert.equal(ui.story(packet(1,'Nedočtené.',false,3),now+34000),'reading','Restart nesmí potvrdit nedočtenou repliku.');
ui.tick(now+35800);ui.clear();
assert.equal(ui.story(packet(1,'Nedočtené.',false,3),now+36000),'done','Dočtená replika se po restartu neopakuje.');
console.log('OK: Story stránky/časování, celé dočtení, opakované ACK, pauza/obnova, generační reset a Standardní parser.');
