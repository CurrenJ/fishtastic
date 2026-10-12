import {THEME,TYPES,preset,element,bounds,validate,rasterize,slots,javaLayout} from './renderer.mjs';
const $=id=>document.getElementById(id), storageKey='fishtastic.gui-workbench.v1';
let project=preset(), selected=null, zoom=3, undo=[], redo=[], drag=null, pendingPreset=null, dirty=false;
try{const saved=localStorage.getItem(storageKey);if(saved)project=validate(JSON.parse(saved));}catch{}
const canvas=$('canvas'),ctx=canvas.getContext('2d'),overlay=$('overlay'),octx=overlay.getContext('2d');
const symbols={panel:'▣',slot:'□',grid:'▦',button:'▰',rect:'■',separator:'━',arrow:'➜',flame:'♨',progress:'▬',heart:'♥',sprite:'▧',label:'T'};
const selection=()=>project.elements.find(e=>e.id===selected);
const clone=v=>structuredClone(v);
function toast(message){$('toast').textContent=message;$('toast').classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').classList.remove('show'),3500);}
function checkpoint(){undo.push(JSON.stringify(project));if(undo.length>80)undo.shift();redo=[];}
function changed(){dirty=true;try{localStorage.setItem(storageKey,JSON.stringify(project));$('save-state').textContent='Saved locally';}catch{$('save-state').textContent='Save JSON to keep this design';}render();}
function edit(fn){checkpoint();fn();changed();inspector();}
function download(name,blob){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function saveJson(){download(`${safeName()}.json`,new Blob([JSON.stringify(project,null,2)+'\n'],{type:'application/json'}));dirty=false;toast('Design JSON saved. Includes all layers and sprite pixels.');}
const safeName=()=>project.name.replace(/[^a-zA-Z0-9_-]/g,'_')||'minecraft_gui';
function codeText(){if($('output-kind').value==='java')return javaLayout(project);if($('output-kind').value==='design')return JSON.stringify(project,null,2);return JSON.stringify(metadata(),null,2);}
function metadata(){return {name:project.name,atlas:project.atlas,panels:project.elements.filter(e=>e.type==='panel').map(e=>({name:e.name,...bounds(e)})),slots:slots(project),labels:project.elements.filter(e=>e.type==='label').map(e=>({text:e.text,x:e.x,y:e.y,w:e.w,align:e.align||'left',baked:e.bake})),regions:project.elements.filter(e=>e.visible!==false&& !['label','grid'].includes(e.type)).map(e=>({id:e.id,name:e.name,type:e.type,...bounds(e)}))};}
function render(){
 const image=rasterize(project,{guides:$('guides').checked});canvas.width=image.width;canvas.height=image.height;ctx.putImageData(new ImageData(image.data,image.width,image.height),0,0);
 canvas.style.width=`${image.width*zoom}px`;canvas.style.height=`${image.height*zoom}px`;overlay.width=image.width*zoom;overlay.height=image.height*zoom;overlay.style.width=canvas.style.width;overlay.style.height=canvas.style.height;
 drawOverlay();$('dimensions').textContent=`${project.atlas.w} × ${project.atlas.h} atlas · ${project.elements.length} layers`;
 $('project-name').value=project.name;$('atlas-w').value=project.atlas.w;$('atlas-h').value=project.atlas.h;$('code').textContent=codeText();$('undo').disabled=!undo.length;$('redo').disabled=!redo.length;layers();warnings();
}
function drawOverlay(){
 octx.clearRect(0,0,overlay.width,overlay.height);
 if($('grid').checked&&zoom>=3){octx.strokeStyle='#ffffff18';octx.lineWidth=1;octx.beginPath();for(let x=0;x<=overlay.width;x+=zoom){octx.moveTo(x+.5,0);octx.lineTo(x+.5,overlay.height);}for(let y=0;y<=overlay.height;y+=zoom){octx.moveTo(0,y+.5);octx.lineTo(overlay.width,y+.5);}octx.stroke();}
 const e=selection();if(!e||e.visible===false)return;const b=bounds(e);octx.strokeStyle='#97d4ad';octx.lineWidth=1.5;octx.setLineDash([4,3]);octx.strokeRect(b.x*zoom-.5,b.y*zoom-.5,b.w*zoom+1,b.h*zoom+1);octx.setLineDash([]);octx.fillStyle='#97d4ad';octx.fillRect((b.x+b.w)*zoom-4,(b.y+b.h)*zoom-4,8,8);
}
function layers(){
 $('layers').replaceChildren();$('layer-count').textContent=`(${project.elements.length})`;
 for(const e of [...project.elements].reverse()){
  const row=document.createElement('div');row.className='layer'+(e.id===selected?' selected':'');const show=document.createElement('button');show.className='visibility';show.textContent=e.visible===false?'○':'●';show.title=e.visible===false?'Show layer':'Hide layer';show.onclick=()=>edit(()=>e.visible=e.visible===false);row.append(show);
  const button=document.createElement('button');button.className='select-layer';button.textContent=`${symbols[e.type]} ${e.name}`;button.onclick=()=>select(e.id);row.append(button);const type=document.createElement('small');type.textContent=e.type;row.append(type);$('layers').append(row);
 }
}
function select(id){selected=id;inspector();layers();drawOverlay();}
function field(label,key,kind='number',options=null){
 const e=selection(),wrap=document.createElement('label');wrap.className='field'+(kind==='checkbox'?' checkbox':kind==='color'?' color':'');const text=document.createElement('span');text.textContent=label;
 const input=document.createElement(options?'select':'input');input.dataset.property=key;
 if(options){for(const v of options){const o=document.createElement('option');o.value=v;o.textContent=v;input.append(o);}input.value=e[key]??options[0];}else{input.type=kind;if(kind==='checkbox')input.checked=!!e[key];else input.value=e[key]??'';if(kind==='number'){input.step=key==='value'?'0.05':'1';input.min=['x','y'].includes(key)?'-4096':'0';input.max=key==='value'?'1':'4096';}}
 input.onchange=()=>{
  let value=kind==='checkbox'?input.checked:kind==='number'?Number(input.value):input.value;
  if(kind==='number'&&!Number.isFinite(value))return;
  const candidate=clone(project),target=candidate.elements.find(v=>v.id===e.id);target[key]=value;
  if(target.type==='heart'&&key==='style'){[target.w,target.h]=value==='small'?[5,4]:[8,7];}
  if(target.type==='grid'){target.w=target.cols*target.cell+(target.cols-1)*target.gap;target.h=target.rows*target.cell+(target.rows-1)*target.gap;}
  try{validate(candidate);edit(()=>project=candidate);}catch(error){toast(error.message);inspector();}
 };if(kind==='checkbox'){wrap.append(input,text);}else wrap.append(text,input);return wrap;
}
function inspector(){
 const e=selection();$('selection-actions').hidden=!e;$('properties').replaceChildren();$('selection-title').textContent=e?'Element properties':'Select an element';$('selection-type').textContent=e?.type||'';
 if(!e){const p=document.createElement('p');p.className='hint';p.textContent='Click the canvas or a layer to edit its dimensions, style, and position.';$('properties').append(p);return;}
 const add=(...args)=>$('properties').append(field(...args));add('Layer name','name','text');
 const pair=(a,b)=>{const row=document.createElement('div');row.className='field-pair';row.append(field(...a),field(...b));$('properties').append(row);};pair(['X','x'],['Y','y']);
 if(e.type!=='grid')pair(['Width','w'],['Height','h']);
 if(e.type==='panel'){add('Bevel thickness','bevel');add('Rounded corners','rounded','checkbox');}
 if(e.type==='slot'||e.type==='grid'){add('Slot style','style','text',['normal','large']);add('First container index','index');}
 if(e.type==='grid'){pair(['Columns','cols'],['Rows','rows']);pair(['Cell size','cell'],['Gap','gap']);}
 if(e.type==='heart')add('Heart style (8x7 / 5x4)','style','text',['normal','small']);
 if(e.type==='button')add('State','state','text',['normal','pressed','disabled']);
 if(e.type==='arrow'||e.type==='progress'){add('Fill direction','direction','text',e.type==='arrow'?['right','left']:['right','left','up','down']);}
 if(['arrow','flame','progress'].includes(e.type))add('Fill amount (0–1)','value');
 if(e.color)add('Color','color','color');
 if(e.type==='label'){add('Text (preview uses a small pixel font)','text','text');add('Alignment','align','text',['left','center','right']);add('Bake into texture','bake','checkbox');}
 if(e.type==='sprite'){add('Nine-slice border (0 = stretch)','slice');const p=document.createElement('p');p.className='hint';p.textContent=`Source: ${e.sourceW} × ${e.sourceH}. Transparent pixels are preserved.`;$('properties').append(p);}
 add('Visible','visible','checkbox');add('Lock movement','locked','checkbox');
}
function warnings(){
 const list=[];for(const e of project.elements){if(e.visible===false)continue;const b=bounds(e);if(b.x<0||b.y<0||b.x+b.w>project.atlas.w||b.y+b.h>project.atlas.h)list.push(`${e.name} extends outside the atlas; those pixels will be clipped.`);if(e.type==='slot'&&(e.w<18||e.h<18))list.push(`${e.name}: a vanilla item needs a 16 × 16 interior.`);if(e.type==='label'&&!e.bake)list.push(`${e.name}: preview guide; excluded from PNG.`);if(e.type==='grid'&&e.cell<(e.style==='large'?26:18))list.push(`${e.name}: slot interiors are smaller than 16 px.`);}
 $('warnings').replaceChildren();for(const warning of list){const li=document.createElement('li');li.textContent=warning;$('warnings').append(li);}if(!list.length){const li=document.createElement('li');li.className='ok';li.textContent='All elements fit inside the atlas.';$('warnings').append(li);}
}
function palette(){
 $('palette').replaceChildren();for(const key of Object.keys(THEME)){const label=document.createElement('label');label.className='field color';label.textContent=key[0].toUpperCase()+key.slice(1);const input=document.createElement('input');input.type='color';input.value=project.theme[key];input.onchange=()=>edit(()=>project.theme[key]=input.value);label.append(input);$('palette').append(label);}
}
function loadPreset(name){if(dirty){pendingPreset=name;$('preset-dialog').showModal();}else applyPreset(name);}
function applyPreset(name){edit(()=>{project=preset(name);selected=null;});dirty=false;palette();document.querySelectorAll('[data-preset]').forEach(b=>b.classList.toggle('active',b.dataset.preset===name));toast(`${name[0].toUpperCase()+name.slice(1)} preset loaded.`);}
for(const name of ['furnace','crafting','hopper','chest','companions']){const button=document.createElement('button');button.dataset.preset=name;button.textContent=name[0].toUpperCase()+name.slice(1);button.onclick=()=>loadPreset(name);$('presets').append(button);}
for(const type of TYPES.filter(v=>v!=='sprite')){const button=document.createElement('button');button.textContent=`${symbols[type]} ${type[0].toUpperCase()+type.slice(1)}`;button.onclick=()=>edit(()=>{const e=element(type,8,18);e.name=`${type[0].toUpperCase()+type.slice(1)} ${project.elements.filter(v=>v.type===type).length+1}`;project.elements.push(e);selected=e.id;});$('elements').append(button);}
const point=event=>{const r=overlay.getBoundingClientRect();return{x:Math.floor((event.clientX-r.left)/zoom),y:Math.floor((event.clientY-r.top)/zoom)};};
overlay.onpointerdown=event=>{
 if(event.button!==0)return;const p=point(event),active=selection(),b=active&&bounds(active);
 const resize=b&&Math.abs(p.x-b.x-b.w)<6/zoom&&Math.abs(p.y-b.y-b.h)<6/zoom;
 const hit=resize?active:[...project.elements].reverse().find(e=>{if(e.visible===false||e.locked)return false;const r=bounds(e);return p.x>=r.x&&p.y>=r.y&&p.x<r.x+r.w&&p.y<r.y+r.h;});
 select(hit?.id||null);if(!hit||hit.locked)return;overlay.focus();drag={id:hit.id,start:p,original:clone(hit),resize,before:JSON.stringify(project),moved:false};overlay.setPointerCapture(event.pointerId);
};
overlay.onpointermove=event=>{
 const p=point(event);$('coordinates').textContent=`x ${p.x} · y ${p.y}`;if(!drag)return;const e=selection(),snap=Number($('snap').value),round=v=>Math.round(v/snap)*snap,dx=p.x-drag.start.x,dy=p.y-drag.start.y;
 if(dx||dy)drag.moved=true;
 if(drag.resize){if(e.type==='grid'){e.cols=Math.max(1,Math.min(128,Math.round((bounds(drag.original).w+dx+e.gap)/(e.cell+e.gap))));e.rows=Math.max(1,Math.min(128,Math.round((bounds(drag.original).h+dy+e.gap)/(e.cell+e.gap))));e.w=bounds(e).w;e.h=bounds(e).h;}else{e.w=Math.max(1,Math.min(4096,round(drag.original.w+dx)));e.h=Math.max(1,Math.min(4096,round(drag.original.h+dy)));}}else{e.x=Math.max(-4096,Math.min(4096,round(drag.original.x+dx)));e.y=Math.max(-4096,Math.min(4096,round(drag.original.y+dy)));}
 render();
};
function finishDrag(){if(!drag)return;if(drag.moved){undo.push(drag.before);if(undo.length>80)undo.shift();redo=[];changed();}drag=null;inspector();}
overlay.onpointerup=finishDrag;overlay.onpointercancel=finishDrag;
$('undo').onclick=()=>{if(!undo.length)return;redo.push(JSON.stringify(project));project=JSON.parse(undo.pop());selected=null;changed();inspector();palette();};
$('redo').onclick=()=>{if(!redo.length)return;undo.push(JSON.stringify(project));project=JSON.parse(redo.pop());selected=null;changed();inspector();palette();};
$('duplicate').onclick=()=>{const e=selection();if(e)edit(()=>{const copy=clone(e);copy.id=element(e.type).id;copy.name+=' copy';copy.x+=4;copy.y+=4;project.elements.push(copy);selected=copy.id;});};
$('delete').onclick=()=>{if(selection())edit(()=>{project.elements=project.elements.filter(e=>e.id!==selected);selected=null;});};
function reorder(delta){const i=project.elements.findIndex(e=>e.id===selected),target=i+delta;if(i<0||target<0||target>=project.elements.length)return;edit(()=>{const e=project.elements.splice(i,1)[0];project.elements.splice(target,0,e);});}
$('raise').onclick=()=>reorder(1);$('lower').onclick=()=>reorder(-1);
document.querySelectorAll('[data-align]').forEach(button=>button.onclick=()=>{const e=selection(),panel=project.elements.find(v=>v.type==='panel'&&v.id!==selected);if(!e||!panel)return;const b=bounds(e),p=bounds(panel);edit(()=>{switch(button.dataset.align){case'left':e.x=p.x+7;break;case'right':e.x=p.x+p.w-b.w-7;break;case'center':e.x=p.x+Math.round((p.w-b.w)/2);break;case'top':e.y=p.y+7;break;case'bottom':e.y=p.y+p.h-b.h-7;break;case'middle':e.y=p.y+Math.round((p.h-b.h)/2);break;}});});
document.addEventListener('keydown',event=>{
 if(['INPUT','SELECT','TEXTAREA'].includes(event.target.tagName)||$('preset-dialog').open)return;
 if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='z'){event.preventDefault();$(event.shiftKey?'redo':'undo').click();return;}
 if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();saveJson();return;}
 if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='d'){event.preventDefault();$('duplicate').click();return;}
 if(event.key==='Delete'||event.key==='Backspace'){event.preventDefault();$('delete').click();return;}
 const e=selection();if(!e||e.locked)return;const delta=event.shiftKey?10:1;if(event.key.startsWith('Arrow')){event.preventDefault();edit(()=>{if(event.key==='ArrowLeft')e.x-=delta;if(event.key==='ArrowRight')e.x+=delta;if(event.key==='ArrowUp')e.y-=delta;if(event.key==='ArrowDown')e.y+=delta;});}
});
$('zoom').onchange=()=>{zoom=Number($('zoom').value);render();};$('guides').onchange=render;$('grid').onchange=drawOverlay;$('output-kind').onchange=render;
$('project-name').onchange=()=>edit(()=>project.name=$('project-name').value||'minecraft_gui');
for(const key of ['w','h'])$('atlas-'+key).onchange=()=>{const value=Number($('atlas-'+key).value);if(!Number.isInteger(value)||value<16||value>2048){toast('Atlas dimensions must be 16–2048 whole pixels.');render();return;}edit(()=>project.atlas[key]=value);};
$('reset-palette').onclick=()=>{edit(()=>project.theme={...THEME});palette();};
$('save').onclick=saveJson;$('load').onclick=()=>$('json-file').click();
$('json-file').onchange=async event=>{const file=event.target.files[0];if(!file)return;try{if(file.size>8_000_000)throw Error('Design files must be under 8 MB.');const input=validate(JSON.parse(await file.text()));edit(()=>{project=input;selected=null;});palette();toast('Design loaded. Undo returns to your previous design.');}catch(error){toast(error.message);}event.target.value='';};
$('import-sprite').onclick=()=>$('sprite-file').click();
$('sprite-file').onchange=async event=>{const file=event.target.files[0];if(!file)return;try{if(file.size>4_000_000)throw Error('PNG sprites must be under 4 MB.');const bitmap=await createImageBitmap(file);if(bitmap.width>512||bitmap.height>512){bitmap.close();throw Error('Sprite dimensions must be at most 512 × 512.');}const temp=document.createElement('canvas');temp.width=bitmap.width;temp.height=bitmap.height;const c=temp.getContext('2d');c.drawImage(bitmap,0,0);const pixels=Array.from(c.getImageData(0,0,temp.width,temp.height).data);bitmap.close();edit(()=>{const e={...element('sprite'),name:file.name,w:temp.width,h:temp.height,sourceW:temp.width,sourceH:temp.height,pixels};project.elements.push(e);selected=e.id;});toast('PNG imported. Set a nine-slice border in element properties to resize its frame.');}catch(error){toast(error.message);}event.target.value='';};
$('export-png').onclick=()=>{const image=rasterize(project),temp=document.createElement('canvas');temp.width=image.width;temp.height=image.height;temp.getContext('2d').putImageData(new ImageData(image.data,image.width,image.height),0,0);temp.toBlob(blob=>{if(blob){download(`${safeName()}.png`,blob);toast('PNG atlas exported at native resolution. Preview guides excluded.');}},'image/png');};
$('download-layout').onclick=()=>download(`${safeName()}.layout.json`,new Blob([JSON.stringify(metadata(),null,2)],{type:'application/json'}));
$('copy').onclick=async()=>{try{await navigator.clipboard.writeText(codeText());toast('Integration output copied.');}catch{toast('Clipboard unavailable. Select the output text or export the layout instead.');}};
$('preset-cancel').onclick=()=>$('preset-dialog').close();$('preset-confirm').onclick=()=>{$('preset-dialog').close();applyPreset(pendingPreset);};
palette();render();inspector();
// Small read-only automation surface for integration checks and scripting.
window.guiWorkbench={getDesign:()=>clone(project),loadDesign:value=>{const next=validate(value);edit(()=>{project=next;selected=null;});palette();},getPixels:()=>rasterize(project),getLayout:metadata};
