import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:chart-no-axes-combined',
      order: 10,
      title: '基金中心',
    },
    name: 'Fund',
    path: '/fund',
    children: [
      {
        component: () => import('#/views/fund/list/index.vue'),
        meta: {
          icon: 'lucide:list-filter',
          title: '基金实时估值',
        },
        name: 'FundList',
        path: 'list',
      },
      {
        redirect: (to) => ({ path: '/fund/list', query: to.query }),
        meta: {
          hideInMenu: true,
          title: '基金详情（已并入列表抽屉）',
        },
        name: 'FundDetail',
        path: 'detail',
      },
      {
        component: () => import('#/views/fund/sync/index.vue'),
        meta: {
          icon: 'lucide:clipboard-list',
          title: '同步记录',
        },
        name: 'FundSyncRecords',
        path: 'sync-records',
      },
      {
        redirect: { path: '/fund/sync-records' },
        meta: {
          hideInMenu: true,
          title: '同步记录',
        },
        name: 'FundSyncLegacy',
        path: 'sync',
      },
      {
        component: () => import('#/views/fund/config/index.vue'),
        meta: {
          authority: ['superadmin'],
          icon: 'lucide:sliders-horizontal',
          title: '量化配置中心',
        },
        name: 'FundConfig',
        path: 'config',
      },
    ],
  },
];

export default routes;
