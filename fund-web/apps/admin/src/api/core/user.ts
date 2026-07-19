import type { UserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  const result = await requestClient.get<{
    permissions?: string[];
    roles?: string[];
    user?: {
      avatar?: string | number;
      nickName?: string;
      userId?: string | number;
      userName?: string;
    };
  }>('/system/user/getInfo');
  const user = result.user ?? {};

  // RuoYi 的用户信息是 { user, roles, permissions }，转换为 Vben UserInfo 结构。
  return {
    ...user,
    avatar: user.avatar ? String(user.avatar) : '',
    homePath: '/fund/list',
    realName: user.nickName ?? user.userName ?? '',
    roles: result.roles ?? [],
    token: '',
    userId: user.userId ? String(user.userId) : '',
    username: user.userName ?? '',
    permissions: result.permissions ?? [],
  } as UserInfo & { permissions: string[] };
}
