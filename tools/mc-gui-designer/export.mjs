// node tools/mc-gui-designer/export.mjs design.json texture.png
// node tools/mc-gui-designer/export.mjs --preset companions texture.png [design.json]
import {readFileSync,writeFileSync} from 'node:fs';
import {deflateSync} from 'node:zlib';
import {preset,validate,rasterize} from './renderer.mjs';
export function png({width,height,data}) {
 const crc=b=>{let n=0xffffffff;for(const v of b){n^=v;for(let i=0;i<8;i++)n=(n>>>1)^((n&1)?0xedb88320:0);}return (n^0xffffffff)>>>0;};
 const chunk=(type,data)=>{const t=Buffer.from(type),len=Buffer.alloc(4),sum=Buffer.alloc(4);len.writeUInt32BE(data.length);sum.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([len,t,data,sum]);};
 const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(width);ihdr.writeUInt32BE(height,4);ihdr[8]=8;ihdr[9]=6;
 const raw=Buffer.alloc((width*4+1)*height);for(let y=0;y<height;y++)Buffer.from(data.buffer,data.byteOffset+y*width*4,width*4).copy(raw,y*(width*4+1)+1);
 return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ihdr),chunk('IDAT',deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
}
if(process.argv[1]?.endsWith('export.mjs')) {
 try {
  const args=process.argv.slice(2),isPreset=args[0]==='--preset';
  const input=isPreset?preset(args[1]):validate(JSON.parse(readFileSync(args[0],'utf8'))),output=args[isPreset?2:1];
  if(!output)throw Error('Usage: node export.mjs design.json texture.png | --preset companions texture.png [design.json]');
  writeFileSync(output,png(rasterize(input)));
  if(isPreset&&args[3])writeFileSync(args[3],JSON.stringify(input,null,2)+'\n');
  console.log(`Generated ${output} (${input.atlas.w} × ${input.atlas.h})`);
 } catch(error){console.error(error.message);process.exitCode=1;}
}
