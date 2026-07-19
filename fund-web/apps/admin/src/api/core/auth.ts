import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    /** RuoYi 客户端 ID，对应 sys_client.client_id */
    clientId?: string;
    /** RuoYi 授权类型 */
    grantType?: string;
    password?: string;
    /** RuoYi 租户编号，默认租户为 000000 */
    tenantId?: string;
    username?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken?: string;
    access_token?: string;
    clientId?: string;
    expireIn?: number;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  const result = await requestClient.post<AuthApi.LoginResult>(
    '/auth/login',
    data,
  );
  // 后端使用 access_token（JSON 命名），前端 store 统一使用 accessToken。
  return {
    ...result,
    accessToken: result.accessToken ?? result.access_token,
  };
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>('/auth/refresh', {
    withCredentials: true,
  });
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.post('/auth/logout', {
    withCredentials: true,
  });
}
