import type { RouteRecordStringComponent } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户所有菜单
 */
export async function getAllMenusApi() {
  // RuoYi 的动态菜单接口返回 RouterVo，路径与 Vben 的后端菜单模式兼容。
  return requestClient.get<RouteRecordStringComponent[]>(
    '/system/menu/getRouters',
  );
}
