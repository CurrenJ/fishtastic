// Package a portable, offline HTML file. No dependencies, network requests, or bundled game assets.
import {readFileSync,writeFileSync} from 'node:fs';
const read=name=>readFileSync(new URL(name,import.meta.url),'utf8');
const script=read('renderer.mjs').replace(/^export /gm,'')+'\n'+read('app.mjs').replace(/^import .*\r?\n/,'');
const html=read('index.html').replace('<link rel="stylesheet" href="style.css">',`<style>${read('style.css')}</style>`)
 .replace('<script type="module" src="app.mjs"></script>',`<script>(()=>{\n${script.replaceAll('</script','<\\/script')}\n})();</script>`);
const output=new URL('../mc-gui-designer.html',import.meta.url);
writeFileSync(output,html);console.log(`Built ${output.pathname}`);
