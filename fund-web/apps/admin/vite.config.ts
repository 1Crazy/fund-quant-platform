import { defineConfig } from '@vben/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
      ],
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 开发环境直接代理到 RuoYi-Vue-Plus，业务 API 路径不额外增加前缀。
            target: 'http://localhost:8080',
            ws: true,
          },
        },
      },
    },
  };
});
