import { build } from 'vite';
import react from '@vitejs/plugin-react';
import { mkdir, copyFile, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(process.argv[2]);
await build({
  configFile: false, root, base: './', publicDir: false, plugins: [react()],
  build: {
    outDir: output, emptyOutDir: false, target: 'chrome100', cssCodeSplit: false,
    rollupOptions: { input: resolve(root, 'tv.html'), output: { entryFileNames: 'demo.js', chunkFileNames: '[name].js', assetFileNames: '[name][extname]' } },
  },
});
// The TV frame fills the viewport, so cqw == vw. This also supports the TV emulator's Chromium 101.
const css = resolve(output, 'style.css');
await writeFile(css, (await readFile(css, 'utf8')).replace(/cqw\b/g, 'vw'));
const images = ['coastline.jpg', 'feiniu.webp', 'feiniu-banner.png', 'bili-icon.png', 'bili-banner.png', 'tianguang.png', 'tianguang-banner.png', 'thunder-icon.png', 'thunder-banner.png'];
await mkdir(resolve(output, 'assets'), { recursive: true });
for (const image of images) await copyFile(resolve(root, 'public/assets', image), resolve(output, 'assets', image));

await mkdir(resolve(output, 'licenses'), { recursive: true });
for (const [dependency, name] of [['react', 'react'], ['react-dom', 'react-dom'], ['scheduler', 'scheduler'], ['@phosphor-icons/react', 'phosphor-icons-react']]) {
  await copyFile(resolve(root, 'node_modules', dependency, 'LICENSE'), resolve(output, 'licenses', name + '-LICENSE.txt'));
}
