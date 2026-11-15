const assert = require('node:assert/strict'), fs = require('node:fs'), vm = require('node:vm');
const parse = require('./service/protocol').parse;
let focus, closed = 0, now = 1000, tick;
class Element {
    constructor() { this.style={};this.hidden=true;this.children=[];this.value=''; }
    set textContent(t) { this.value=String(t);this.children=[]; }
    get textContent() { return this.value+this.children.map(c=>c.textContent).join(''); }
    appendChild(c) { this.children.push(c); }
    focus() { focus=this; }
    get clientHeight() { return 40; }
    get scrollHeight() { return Math.ceil(Array.from(this.textContent).length/50)*20; }
}
const html=fs.readFileSync(__dirname+'/app/index.html','utf8');
const nodes=Object.fromEntries([...html.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],new Element()]));
nodes.output.hidden=false;
const listeners={},bridges=[],intervals=new Set();let input='com.webos.app.hdmi3';
function Bridge(){bridges.push(this);this.call=(uri,payload)=>{this.uri=uri;this.payload=JSON.parse(payload)};this.cancel=()=>{this.cancelled=true};}
const document={documentElement:{clientHeight:1080},getElementById:id=>nodes[id],createElement:()=>new Element(),
    createTextNode:t=>({textContent:t}),addEventListener:(n,f)=>{listeners[n]=f}};
function XHR(){this.open=()=>{};this.send=()=>{this.responseText=fs.readFileSync(__dirname+'/app/appinfo.json','utf8');this.onload()};}
const window={PalmSystem:{launchParams:'{"resumeOverlay":true}'},close:()=>closed++,addEventListener:(n,f)=>{listeners[n]=f}};
const context=vm.createContext({window,document,PalmServiceBridge:Bridge,XMLHttpRequest:XHR,Date:{now:()=>now},
    localStorage:{getItem:()=>input,setItem:(k,v)=>{input=v}},
    setInterval:f=>{tick=f;intervals.add(f);return f},clearInterval:f=>intervals.delete(f)});
vm.runInContext(fs.readFileSync(__dirname+'/app/receiver-ui.js','utf8'),context);
vm.runInContext(fs.readFileSync(__dirname+'/app/app.js','utf8'),context);
const receiver=()=>bridges.filter(b=>b.uri.endsWith('/listen')).at(-1);
const emit=p=>receiver().onservicecallback(JSON.stringify(p));
const send=text=>emit({returnValue:true,source:'198.51.100.10',packet:parse(Buffer.from(text))});
const key=keyCode=>listeners.keydown({keyCode,preventDefault(){}});
emit({returnValue:true,ready:true,name:'LG PlayCaption · ABCDEF123456'});
key(13);assert.equal(nodes.controls.hidden,false);assert.equal(focus,nodes['change-input']);
assert.ok(nodes['connection-info'].textContent.includes('verze '+JSON.parse(fs.readFileSync(__dirname+'/app/appinfo.json','utf8')).version));
assert.match(nodes['connection-info'].textContent,/Přijímač je připraven/);
assert.match(nodes['connection-info'].textContent,/HDMI 3/);
key(40);key(13);assert.equal(nodes.controls.hidden,true);assert.equal(nodes['test-subtitle'].hidden,false);
assert.equal(nodes.output.hidden,true);assert.match(nodes['test-subtitle'].textContent,/neověřuje spojení/);
now+=5001;tick();assert.equal(nodes['test-subtitle'].hidden,true);assert.equal(nodes.output.hidden,false);
key(461);assert.equal(nodes.controls.hidden,false);assert.match(nodes['connection-info'].textContent,/Poslední data: žádná/);
send('PCO1\t1\t0.1\t0.7\t0.9\t0.95');tick();
assert.match(nodes['connection-info'].textContent,/Zařízení posílá platná data/);
assert.match(nodes['connection-info'].textContent,/poslední titulek: žádný/);
send('PCT1\t1\t6000\tSkutečný text.\t');tick();assert.match(nodes['connection-info'].textContent,/poslední titulek: 0 s/);
emit({returnValue:true,unsupportedStory:true});assert.match(nodes.status.textContent,/nepodporuje Story/);assert.equal(nodes.status.hidden,false);
send('PCO1\t1\t0.1\t0.7\t0.9\t0.95');tick();assert.equal(nodes.status.hidden,false,'Rámeček nesmí schovat nepodporovaný režim.');
send('PCT1\t2\t6000\tStandardní režim.\t');assert.equal(nodes.status.hidden,true);
nodes['toggle-output'].onclick();assert.equal(nodes.output.hidden,true);
key(461);assert.equal(nodes.controls.hidden,true);assert.equal(closed,0,'Zpět z nabídky nesmí zavřít aplikaci.');
now+=6100;tick();key(13);nodes['toggle-output'].onclick();
assert.equal(nodes.output.hidden,false);assert.equal(nodes.subtitle.hidden,true,'Zobrazení nesmí oživit prošlý titulek.');
assert.match(nodes['connection-info'].textContent,/Komunikace byla přerušena/);
emit({returnValue:true,otherSender:true});assert.match(nodes['connection-info'].textContent,/dalšího zařízení/);
send('PCT1\t3\t6000\tObnova.\t');tick();assert.equal(nodes.subtitle.hidden,false);
assert.doesNotMatch(nodes['connection-info'].textContent,/dalšího zařízení/);
bridges.filter(b=>b.uri.endsWith('/restartUserActivityTimer')).at(-1).onservicecallback('{"returnValue":false}');tick();
assert.match(nodes['connection-info'].textContent,/LG odmítá/);
assert.equal(nodes.status.hidden,false);
emit({returnValue:false,errorText:'Příjem selhal'});
now+=45000;tick();
bridges.filter(b=>b.uri.endsWith('/restartUserActivityTimer')).at(-1).onservicecallback('{"returnValue":true}');tick();
assert.match(nodes['connection-info'].textContent,/Příjem selhal/);
receiver().onservicecallback('null');tick();
assert.match(nodes['connection-info'].textContent,/neplatná odpověď/);
nodes['change-input'].onclick();assert.equal(nodes.controls.hidden,true);assert.equal(nodes.inputs.hidden,false);
const list=bridges.filter(b=>b.uri.endsWith('/getAllInputStatus')).at(-1);
list.onservicecallback(JSON.stringify({returnValue:true,devices:[{appId:'com.webos.app.hdmi3'}]}));
nodes['input-current'].onclick();assert.equal(nodes.inputs.hidden,true);
key(461);nodes['close-app'].onclick();assert.equal(closed,1);assert.equal(intervals.size,0);
assert.equal(receiver().cancelled,true);
assert.equal(bridges.some(b=>b.uri.endsWith('/storyAck')),false,'Lokální test/skrytí nic nepotvrzují jako dočtené.');
listeners.webOSRelaunch({detail:{resumeOverlay:true}});assert.equal(nodes.controls.hidden,true);assert.equal(intervals.size,1);
console.log('OK: menu OK/šipky/Zpět, test bez falešného spojení, pravdivé stavy, skrytí bez obnovení prošlého textu, chyba, HDMI, zavření/relaunch.');
