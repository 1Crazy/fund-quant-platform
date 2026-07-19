<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { RUOYI_CLIENT_ID } from '#/api/request';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

const isDev = import.meta.env.DEV;

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      defaultValue: isDev ? 'admin' : '',
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      // 仅开发模式预填初始化密码，生产构建不会携带表单默认凭据。
      defaultValue: isDev ? 'admin123' : '',
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});

async function handleLogin(values: Record<string, unknown>) {
  await authStore.authLogin({
    ...values,
    clientId: RUOYI_CLIENT_ID,
    grantType: 'password',
    tenantId: '000000',
  });
}
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :show-code-login="false"
    :show-forget-password="false"
    :show-qrcode-login="false"
    :show-register="false"
    :show-third-party-login="false"
    sub-title="使用 RuoYi-Vue-Plus 开发账号进入基金量化决策系统"
    title="基金量化决策系统"
    @submit="handleLogin"
  />
  <div
    v-if="isDev"
    class="mx-auto mt-4 max-w-md rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
  >
    <div class="font-medium">开发环境登录</div>
    <div class="mt-1">账号：admin　密码：admin123</div>
    <div class="mt-1 text-xs text-amber-700">
      该提示仅在 Vite 开发模式显示。
    </div>
  </div>
</template>
