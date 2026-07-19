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
        component: () => import('#/views/fund/detail/index.vue'),
        meta: {
          hideInMenu: true,
          title: '基金详情',
        },
        name: 'FundDetail',
        path: 'detail',
      },
    ],
  },
];

export default routes;
