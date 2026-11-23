import { build } from 'vite';
import { createHash } from 'node:crypto';
import { readFile, readdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root=path.dirname(fileURLToPath(import.meta.url));
process.chdir(root);
await build();
const digest=async file=>createHash('sha256').update(await readFile(file)).digest('hex');
async function files(dir){const result=[];for(const entry of await readdir(dir,{withFileTypes:true})){const name=path.join(dir,entry.name);if(entry.isDirectory())result.push(...await files(name));else result.push(name);}return result.sort();}
const input=['package.json','package-lock.json','tsconfig.json','vite.config.ts','index.html','build.mjs','check_assets.py','../app/src/main/assets/fonts/outfit.ttf','../app/src/main/res/raw/flip_clock.wav',...await files(path.join(root,'src')),...await files(path.join(root,'public'))].map(file=>path.resolve(root,file));
const destination=path.resolve(root,'../app/src/main/assets/phone-settings'),source={},assets={};
for(const file of input)source[path.relative(root,file).split(path.sep).join('/')]=await digest(file);
for(const file of await files(destination)){const name=path.relative(destination,file).split(path.sep).join('/');if(name!=='build-manifest.json')assets[name]=await digest(file);}
await writeFile(path.join(destination,'build-manifest.json'),JSON.stringify({version:1,source,assets},null,2)+'\n');
