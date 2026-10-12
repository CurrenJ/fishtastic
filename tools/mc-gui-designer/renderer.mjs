// Shared, dependency-free pixel renderer. Browser previews and CLI PNGs use identical pixels.
export const VERSION = 1;
export const THEME = { face: '#c6c6c6', light: '#ffffff', mid: '#8b8b8b', dark: '#373737', outline: '#000000', recess: '#8b8b8b', accent: '#dc667c' };
export const TYPES = ['panel', 'slot', 'grid', 'button', 'rect', 'separator', 'arrow', 'flame', 'progress', 'heart', 'sprite', 'label'];
const FONT = {
 A:'01110/10001/10001/11111/10001/10001/10001',B:'11110/10001/10001/11110/10001/10001/11110',C:'01111/10000/10000/10000/10000/10000/01111',D:'11110/10001/10001/10001/10001/10001/11110',E:'11111/10000/10000/11110/10000/10000/11111',F:'11111/10000/10000/11110/10000/10000/10000',G:'01111/10000/10000/10111/10001/10001/01111',H:'10001/10001/10001/11111/10001/10001/10001',I:'111/010/010/010/010/010/111',J:'00111/00010/00010/00010/10010/10010/01100',K:'10001/10010/10100/11000/10100/10010/10001',L:'10000/10000/10000/10000/10000/10000/11111',M:'10001/11011/10101/10101/10001/10001/10001',N:'10001/11001/10101/10011/10001/10001/10001',O:'01110/10001/10001/10001/10001/10001/01110',P:'11110/10001/10001/11110/10000/10000/10000',Q:'01110/10001/10001/10001/10101/10010/01101',R:'11110/10001/10001/11110/10100/10010/10001',S:'01111/10000/10000/01110/00001/00001/11110',T:'11111/00100/00100/00100/00100/00100/00100',U:'10001/10001/10001/10001/10001/10001/01110',V:'10001/10001/10001/10001/10001/01010/00100',W:'10001/10001/10001/10101/10101/10101/01010',X:'10001/10001/01010/00100/01010/10001/10001',Y:'10001/10001/01010/00100/00100/00100/00100',Z:'11111/00001/00010/00100/01000/10000/11111',
 '0':'01110/10001/10011/10101/11001/10001/01110','1':'010/110/010/010/010/010/111','2':'01110/10001/00001/00010/00100/01000/11111','3':'11110/00001/00001/01110/00001/00001/11110','4':'00010/00110/01010/10010/11111/00010/00010','5':'11111/10000/10000/11110/00001/00001/11110','6':'01110/10000/10000/11110/10001/10001/01110','7':'11111/00001/00010/00100/01000/01000/01000','8':'01110/10001/10001/01110/10001/10001/01110','9':'01110/10001/10001/01111/00001/00001/01110',
 '.':'0/0/0/0/0/0/1',':':'0/1/1/0/1/1/0','-':'000/000/000/111/000/000/000','?':'01110/10001/00001/00010/00100/00000/00100','!':'1/1/1/1/1/0/1','/':'00001/00001/00010/00100/01000/10000/10000',' ':'000/000/000/000/000/000/000'
};
const id = name => name.toLowerCase().replace(/[^a-z0-9]+/g, '_');
export function element(type, x = 8, y = 18) {
 const defaults = {
  panel:{w:176,h:166,bevel:3,rounded:true},slot:{w:18,h:18,style:'normal',index:0},grid:{w:162,h:54,cols:9,rows:3,cell:18,gap:0,style:'normal',index:9},button:{w:60,h:20,state:'normal'},rect:{w:24,h:12,color:'#8b8b8b'},separator:{w:100,h:2},arrow:{w:24,h:17,value:0,color:'#ffffff',direction:'right'},flame:{w:14,h:14,value:1,color:'#ffb32c'},progress:{w:60,h:8,value:0.6,color:'#dc667c',direction:'right'},heart:{w:8,h:7,color:'#dc667c'},sprite:{w:16,h:16,sourceW:16,sourceH:16,pixels:[],slice:0},label:{w:100,h:9,text:'Inventory',color:'#404040',bake:false,align:'left'}
 };
 return {id:`${type}_${Math.random().toString(36).slice(2,8)}`,type,name:type,x,y,visible:true,...defaults[type]};
}
export function bounds(e) {
 if (e.type === 'grid') return {x:e.x,y:e.y,w:e.cols*e.cell+(e.cols-1)*e.gap,h:e.rows*e.cell+(e.rows-1)*e.gap};
 return {x:e.x,y:e.y,w:e.w,h:e.h};
}
export function preset(name = 'companions') {
 const panelH = name === 'hopper' ? 133 : name === 'chest' ? 222 : name === 'companions' ? 202 : 166;
 const p={version:VERSION,name:id(name),atlas:{w:256,h:256},theme:{...THEME},elements:[]};
 const add=(type,x,y,opts={})=>{const e={...element(type,x,y),...opts};e.id=`${type}_${p.elements.length}`;p.elements.push(e);return e;};
 add('panel',0,0,{h:panelH,name:'Main panel'});
 add('label',8,6,{text:name === 'companions'?'Companions':name[0].toUpperCase()+name.slice(1),name:'Title'});
 const invY=panelH-82;
 add('label',8,invY-12,{text:'Inventory',name:'Inventory label'});
 add('grid',7,invY-1,{name:'Player inventory'});
 add('grid',7,invY+57,{rows:1,index:0,name:'Hotbar'});
 if(name==='companions') {
  add('slot',75,29,{w:26,h:26,style:'large',index:0,name:'Companion'});
  add('heart',180,0,{name:'Header heart'});
  add('heart',190,0,{w:5,h:4,style:'small',name:'Encyclopedia badge heart'});
  add('label',50,18,{text:'Your companion',w:76,align:'center'});
  add('label',8,58,{text:'Companion name',w:160,align:'center'});
  add('label',8,89,{text:'Rename',w:104});
 } else if(name==='furnace') {
  add('slot',55,16,{index:0,name:'Input'});add('slot',55,52,{index:1,name:'Fuel'});add('slot',111,30,{w:26,h:26,style:'large',index:2,name:'Result'});
  add('flame',56,36,{value:0,name:'Fuel indicator'});add('arrow',79,34,{name:'Smelt progress'});
 } else if(name==='crafting') {
  add('grid',29,16,{cols:3,rows:3,index:1,name:'Crafting inputs'});add('arrow',89,35);add('slot',123,34,{w:26,h:26,style:'large',index:0,name:'Result'});
 } else if(name==='hopper') add('grid',43,19,{cols:5,rows:1,index:0,name:'Hopper slots'});
 else if(name==='chest') add('grid',7,17,{rows:6,index:0,name:'Chest slots'});
 return p;
}
export function validate(input) {
 if(!input || input.version !== VERSION || !Array.isArray(input.elements)) throw Error('Expected a version 1 GUI design with an elements array.');
 const p=structuredClone(input);
 for(const key of ['w','h']) if(!Number.isInteger(p.atlas?.[key]) || p.atlas[key]<16 || p.atlas[key]>2048) throw Error('Atlas dimensions must be whole numbers between 16 and 2048.');
 if(p.elements.length>500) throw Error('A design can contain at most 500 elements.');
 p.theme={...THEME,...p.theme};
 const ids=new Set();
 for(const e of p.elements) {
  if(!TYPES.includes(e.type)) throw Error(`Unknown element type: ${e.type}`);
  if(typeof e.id!=='string'||ids.has(e.id)) throw Error('Every element needs a unique string ID.'); ids.add(e.id);
  for(const key of ['x','y','w','h']) if(!Number.isFinite(e[key]) || !Number.isInteger(e[key]) || Math.abs(e[key])>4096 || ((key==='w'||key==='h')&&e[key]<1)) throw Error(`Invalid ${key} on ${e.name || e.id}.`);
  if(e.type==='grid') for(const key of ['cols','rows','cell','gap']) if(!Number.isInteger(e[key])||e[key]<(key==='gap'?0:1)||e[key]>128) throw Error(`Invalid grid ${key}.`);
  if(e.type==='sprite' && (!Number.isInteger(e.sourceW)||!Number.isInteger(e.sourceH)||e.sourceW<1||e.sourceH<1||e.sourceW>512||e.sourceH>512||!Array.isArray(e.pixels)||(e.pixels.length!==0 && e.pixels.length!==e.sourceW*e.sourceH*4)||e.pixels.some(v=>!Number.isInteger(v)||v<0||v>255))) throw Error('Sprite pixels must be RGBA bytes, at most 512 × 512.');
  if(e.align!==undefined&&!['left','center','right'].includes(e.align)) throw Error(`Invalid text alignment on ${e.name || e.id}.`);
  if(e.value!==undefined&&(!Number.isFinite(e.value)||e.value<0||e.value>1)) throw Error('Progress must be between 0 and 1.');
  if(e.slice!==undefined&&(!Number.isInteger(e.slice)||e.slice<0||e.slice>Math.min(e.sourceW,e.sourceH)/2)) throw Error('Invalid nine-slice border.');
 }
 return p;
}
export function rasterize(project, {guides=false}={}) {
 const {w:width,h:height}=project.atlas, data=new Uint8ClampedArray(width*height*4),t={...THEME,...project.theme};
 const rgba=c=>{if(Array.isArray(c)) return c;const s=String(c||'#000000').replace('#','');return [parseInt(s.slice(0,2),16)||0,parseInt(s.slice(2,4),16)||0,parseInt(s.slice(4,6),16)||0,s.length===8?parseInt(s.slice(6,8),16):255];};
 const pixel=(x,y,c)=>{x=Math.floor(x);y=Math.floor(y);if(x<0||y<0||x>=width||y>=height) return;const a=rgba(c),i=(y*width+x)*4,alpha=a[3]/255,old=data[i+3]/255,out=alpha+old*(1-alpha);if(!out)return;for(let j=0;j<3;j++)data[i+j]=(a[j]*alpha+data[i+j]*old*(1-alpha))/out;data[i+3]=out*255;};
 const rect=(x,y,w,h,c)=>{for(let yy=Math.max(0,y);yy<Math.min(height,y+h);yy++)for(let xx=Math.max(0,x);xx<Math.min(width,x+w);xx++)pixel(xx,yy,c);};
 const bevel=(x,y,w,h,fill,hi,lo,n=1)=>{rect(x,y,w,h,fill);for(let i=0;i<n;i++){rect(x+i,y+i,w-i*2,1,hi);rect(x+i,y+i,1,h-i*2,hi);rect(x+i,y+h-1-i,w-i*2,1,lo);rect(x+w-1-i,y+i,1,h-i*2,lo);}};
 const slot=(x,y,w,h,style)=>{if(style==='large'){bevel(x,y,w,h,t.face,t.light,t.dark,1);bevel(x+4,y+4,w-8,h-8,t.recess,t.dark,t.light,1);}else bevel(x,y,w,h,t.recess,t.dark,t.light,1);};
 const mask=(x,y,w,h,rows,c)=>{const mw=rows[0].length,mh=rows.length;for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++)if(rows[Math.floor(yy*mh/h)][Math.floor(xx*mw/w)]==='1')pixel(x+xx,y+yy,c);};
 const glyph=char=>(FONT[char.toUpperCase()]||FONT['?']).split('/');
 // Each line aligns within the box the way Java centres text: floor((w - width) / 2).
 const text=(e)=>{String(e.text).split('\n').forEach((line,i)=>{const width=Math.max(0,[...line].reduce((sum,c)=>sum+glyph(c)[0].length+1,0)-1);let x=e.x+(e.align==='center'?Math.floor((e.w-width)/2):e.align==='right'?e.w-width:0);for(const char of line){const rows=glyph(char);mask(x,e.y+i*9,rows[0].length,7,rows,e.color);x+=rows[0].length+1;}});};
 for(const e of project.elements) {
  if(e.visible===false)continue; const {x,y,w,h}=bounds(e);
  switch(e.type) {
   case 'panel': {
    const n=Math.min(Math.max(1,e.bevel||3),Math.floor(Math.min(w,h)/2));
    bevel(x,y,w,h,t.outline,t.outline,t.outline,1);bevel(x+1,y+1,w-2,h-2,t.face,t.light,t.dark,n-1);
    if(e.rounded){for(const [xx,yy] of [[x,y],[x+w-1,y],[x,y+h-1],[x+w-1,y+h-1]]){const i=(yy*width+xx)*4;if(xx>=0&&yy>=0&&xx<width&&yy<height)data.fill(0,i,i+4);}}
    break;
   }
   case 'slot':slot(x,y,w,h,e.style);break;
   case 'grid':for(let r=0;r<e.rows;r++)for(let c=0;c<e.cols;c++)slot(x+c*(e.cell+e.gap),y+r*(e.cell+e.gap),e.cell,e.cell,e.style);break;
   case 'button':bevel(x,y,w,h,t.outline,t.outline,t.outline);bevel(x+1,y+1,w-2,h-2,e.state==='disabled'?t.mid:t.face,e.state==='pressed'?t.dark:t.light,e.state==='pressed'?t.light:t.dark,2);break;
   case 'rect':rect(x,y,w,h,e.color);break;
   case 'separator':rect(x,y,w,1,t.dark);rect(x,y+1,w,1,t.light);break;
   // Masks scale to the box; draw at 8x7 (normal) or 5x4 (small) or whole multiples to stay pixel-exact.
   case 'heart':mask(x,y,w,h,e.style==='small'?['01010','11111','01110','00100']:['01100110','11111111','11111111','11111111','01111110','00111100','00011000'],e.color);break;
   case 'arrow': {
    const tip=Math.min(Math.floor(h/2),Math.floor(w/2));
    for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){const px=e.direction==='left'?w-1-xx:xx,dy=Math.abs(yy-(h-1)/2),inside=px<w-tip?dy<h/6:dy<(w-px)*h/(2*tip);if(inside)pixel(x+xx,y+yy,xx/w<e.value?e.color:t.mid);}
    break;
   }
   case 'flame':for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){const nx=(xx-(w-1)/2)/(w/2),ny=yy/h,edge=Math.sin(ny*Math.PI/1.6)*0.9;if(Math.abs(nx)<edge)pixel(x+xx,y+yy,yy>=h*(1-e.value)?e.color:t.mid);}break;
   case 'progress': {
    bevel(x,y,w,h,t.recess,t.dark,t.light);const innerW=w-2,innerH=h-2;
    if(e.direction==='up'||e.direction==='down'){const hh=Math.round(innerH*e.value);rect(x+1,y+1+(e.direction==='up'?innerH-hh:0),innerW,hh,e.color);}else{const ww=Math.round(innerW*e.value);rect(x+1+(e.direction==='left'?innerW-ww:0),y+1,ww,innerH,e.color);}break;
   }
   case 'label':if(e.bake||guides)text(e);break;
   case 'sprite': {
    if(!e.pixels.length)break;
    const slice=e.slice||0;
    const map=(v,d,s)=>{const b=Math.min(slice,Math.floor(d/2));return !b?Math.floor(v*s/d):v<b?v:v>=d-b?s-(d-v):b+Math.floor((v-b)*(s-b*2)/(d-b*2));};
    for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){const sx=Math.min(e.sourceW-1,map(xx,w,e.sourceW)),sy=Math.min(e.sourceH-1,map(yy,h,e.sourceH)),i=(sy*e.sourceW+sx)*4;pixel(x+xx,y+yy,e.pixels.slice(i,i+4));}break;
   }
  }
 }
 return {width,height,data};
}
export function slots(project) {
 const out=[];
 for(const e of project.elements)if(e.visible!==false&&(e.type==='slot'||e.type==='grid')) {
  const rows=e.type==='grid'?e.rows:1,cols=e.type==='grid'?e.cols:1;
  for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){const inset=e.style==='large'?5:1;out.push({name:e.name,index:(e.index||0)+r*cols+c,x:e.x+c*((e.cell||e.w)+(e.gap||0))+inset,y:e.y+r*((e.cell||e.h)+(e.gap||0))+inset});}
 }
 return out;
}
export function javaLayout(project) {
 const panel=project.elements.find(e=>e.type==='panel');
 return `// Generated from ${project.name}.json. Coordinates are panel-relative.\n// Choose each slot's container (companion / inventory / inputs) before using this code.\nthis.imageWidth = ${panel?.w||176};\nthis.imageHeight = ${panel?.h||166};\n\n`+slots(project).map(s=>`addSlot(new Slot(/* ${s.name.replaceAll('*/','')} container */ container, ${s.index}, ${s.x-(panel?.x||0)}, ${s.y-(panel?.y||0)}));`).join('\n');
}
