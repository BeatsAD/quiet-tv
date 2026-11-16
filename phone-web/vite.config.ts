import { defineConfig } from 'vite';
export default defineConfig({
  base: './',
  build: {
    outDir: '../app/src/main/assets/phone-settings',
    emptyOutDir: false,
    cssCodeSplit: false,
    rolldownOptions: { output: { entryFileNames: 'app.js', assetFileNames: asset => asset.names.some(name => name.endsWith('.css')) ? 'styles.css' : 'assets/[name][extname]' } },
  },
});
