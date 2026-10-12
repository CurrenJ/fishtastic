import test from 'node:test';
import assert from 'node:assert/strict';
import {inflateSync} from 'node:zlib';
import {readFileSync} from 'node:fs';
import {preset,element,validate,rasterize,slots,bounds} from './renderer.mjs';
import {png} from './export.mjs';
const at=(image,x,y)=>Array.from(image.data.slice((y*image.width+x)*4,(y*image.width+x)*4+4));

test('all shipped presets are procedural, valid, and fit the atlas',()=>{
 for(const name of ['furnace','crafting','hopper','chest','companions']){
  const p=validate(preset(name));assert.ok(p.elements.every(e=>e.type!=='sprite'));
  for(const e of p.elements){const b=bounds(e);assert.ok(b.x>=0&&b.y>=0&&b.x+b.w<=p.atlas.w&&b.y+b.h<=p.atlas.h);}
 }
 const saved=validate(JSON.parse(readFileSync(new URL('./companions.json',import.meta.url),'utf8')));
 assert.ok(saved.elements.every(e=>e.type!=='sprite'),'saved companion design must contain no imported pixels');
});
test('vanilla slot interiors match the companion menu and inventory coordinates',()=>{
 const list=slots(preset());assert.equal(list.length,37);
 assert.deepEqual(list.find(s=>s.name==='Companion'),{name:'Companion',index:0,x:80,y:34});
 assert.deepEqual(list.find(s=>s.name==='Player inventory'),{name:'Player inventory',index:9,x:8,y:120});
 assert.deepEqual(list.find(s=>s.name==='Hotbar'),{name:'Hotbar',index:0,x:8,y:178});
 const p=preset('furnace');assert.deepEqual(slots(p).find(s=>s.name==='Result'),{name:'Result',index:2,x:116,y:35});
});
test('preview guides never leak into the exported texture',()=>{
 const p=preset('furnace'),exported=rasterize(p),preview=rasterize(p,{guides:true});
 assert.deepEqual(at(exported,8,6),[198,198,198,255]);assert.notDeepEqual(preview.data,exported.data);
 for(const label of p.elements.filter(e=>e.type==='label'))label.visible=false;
 assert.deepEqual(rasterize(p,{guides:true}).data,exported.data);
});
test('transparent atlas, layer visibility, and exact bevel colors',()=>{
 const p=preset('furnace'),image=rasterize(p);
 assert.deepEqual(at(image,255,255),[0,0,0,0]);assert.deepEqual(at(image,55,16),[55,55,55,255]);
 assert.deepEqual(at(image,56,17),[139,139,139,255]);assert.deepEqual(at(image,72,33),[255,255,255,255]);
 p.elements.find(e=>e.name==='Input').visible=false;assert.deepEqual(at(rasterize(p),55,16),[198,198,198,255]);
});
test('sprites preserve transparency, nearest-neighbor pixels, and nine-slice corners',()=>{
 const p={...preset(),atlas:{w:16,h:16},elements:[]};
 p.elements.push({...element('rect',0,0),w:16,h:16,color:'#112233'});
 p.elements.push({...element('sprite',2,2),w:6,h:6,sourceW:3,sourceH:3,slice:1,
  pixels:[255,0,0,255,0,0,0,0,0,255,0,255,0,0,0,0,0,0,255,255,0,0,0,0,255,255,0,255,0,0,0,0,255,0,255,255]});
 const im=rasterize(validate(p));assert.deepEqual(at(im,2,2),[255,0,0,255]);assert.deepEqual(at(im,7,2),[0,255,0,255]);assert.deepEqual(at(im,2,7),[255,255,0,255]);assert.deepEqual(at(im,7,7),[255,0,255,255]);assert.deepEqual(at(im,4,2),[17,34,51,255]);
});
test('progress fills honor every direction and endpoints',()=>{
 for(const direction of ['right','left','up','down']){
  const p={...preset(),atlas:{w:16,h:16},elements:[{...element('progress',2,2),w:10,h:10,color:'#ff0000',direction,value:0}]};
  assert.deepEqual(at(rasterize(p),4,4),[139,139,139,255]);p.elements[0].value=1;
  assert.deepEqual(at(rasterize(p),4,4),[255,0,0,255]);
 }
});
test('JSON import rejects oversized, malformed, duplicate and invalid sprite designs',()=>{
 const p=preset();assert.throws(()=>validate({...p,atlas:{w:10000,h:256}}));
 assert.throws(()=>validate({...p,elements:[{...p.elements[0],x:NaN}]}));
 assert.throws(()=>validate({...p,elements:[p.elements[0],p.elements[0]]}));
 assert.throws(()=>validate({...p,elements:[{...element('sprite'),pixels:[1]}]}));
 assert.throws(()=>validate({...p,elements:[{...element('grid'),rows:999}]}));
});
test('CLI PNG contains the exact browser raster and RGBA dimensions',()=>{
 const im=rasterize(preset()),encoded=png(im);assert.equal(encoded.readUInt32BE(16),256);assert.equal(encoded.readUInt32BE(20),256);
 let offset=8,idat=[];while(offset<encoded.length){const length=encoded.readUInt32BE(offset),type=encoded.toString('ascii',offset+4,offset+8);if(type==='IDAT')idat.push(encoded.subarray(offset+8,offset+8+length));offset+=12+length;}
 const raw=inflateSync(Buffer.concat(idat));for(let y=0;y<im.height;y++){assert.equal(raw[y*(im.width*4+1)],0);assert.deepEqual(raw.subarray(y*(im.width*4+1)+1,(y+1)*(im.width*4+1)),Buffer.from(im.data.subarray(y*im.width*4,(y+1)*im.width*4)));}
});
test('label alignment places each line within its box',()=>{
 const ink=align=>{const p=validate({version:1,name:'a',atlas:{w:32,h:16},elements:[{...element('label',0,0),id:'l',w:20,text:'I',color:'#ff0000',bake:true,align}]});
  const d=rasterize(p).data??rasterize(p);const xs=[];for(let x=0;x<32;x++)if(d[x*4+3])xs.push(x);return [xs[0],xs.at(-1)];};
 assert.deepEqual(ink('left'),[0,2]);assert.deepEqual(ink('center'),[8,10]);assert.deepEqual(ink('right'),[17,19]);
 assert.throws(()=>validate({version:1,name:'a',atlas:{w:32,h:16},elements:[{...element('label'),id:'l',align:'middle'}]}));
});
