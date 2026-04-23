import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { isAdminAuthenticated } from '../shared/auth/adminAuth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'BusinessChat',
    component: () => import('../views/BusinessChatView.vue'),
    meta: {
      title: '业务对话'
    }
  },
  {
    path: '/model-config',
    name: 'ModelConfig',
    component: () => import('../views/ModelConfigView.vue'),
    meta: {
      title: '模型配置'
    }
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('../views/AdminLoginView.vue'),
    meta: {
      layout: 'fullscreen',
      title: '管理后台登录'
    }
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayoutView.vue'),
    meta: {
      requiresAdminAuth: true
    },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard'
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('../views/admin/AdminDashboardView.vue'),
        meta: {
          title: '运营总览'
        }
      },
      {
        path: 'documents',
        name: 'AdminDocuments',
        component: () => import('../views/admin/AdminDocumentListView.vue'),
        meta: {
          title: '文档接入'
        }
      },
      {
        path: 'documents/:documentId',
        name: 'AdminDocumentDetail',
        component: () => import('../views/admin/AdminDocumentDetailView.vue'),
        meta: {
          title: '文档详情'
        }
      },
      {
        path: 'knowledge-route',
        name: 'AdminKnowledgeRoute',
        component: () => import('../views/admin/AdminKnowledgeRouteView.vue'),
        meta: {
          title: '知识路由'
        }
      },
      {
        path: 'knowledge-route/traces',
        name: 'AdminKnowledgeRouteTrace',
        component: () => import('../views/admin/AdminKnowledgeRouteTraceView.vue'),
        meta: {
          title: '路由追踪'
        }
      },
      {
        path: 'observability',
        name: 'AdminObservabilityList',
        component: () => import('../views/admin/AdminObservabilityListView.vue'),
        meta: {
          title: '对话观测'
        }
      },
      {
        path: 'api-catalog',
        name: 'AdminApiCatalog',
        component: () => import('../pages/ApiCatalogPage.vue'),
        meta: {
          title: '前端 API 目录'
        }
      },
      {
        path: 'observability/:conversationId',
        name: 'AdminObservabilitySession',
        component: () => import('../views/admin/AdminObservabilitySessionView.vue'),
        meta: {
          title: '会话链路'
        }
      },
      {
        path: 'observability/:conversationId/exchanges/:exchangeId',
        name: 'AdminObservabilityExchangeDetail',
        component: () => import('../views/admin/AdminObservabilityDetailView.vue'),
        meta: {
          title: '轮次详情'
        }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  if (to.meta.requiresAdminAuth && !isAdminAuthenticated()) {
    return {
      name: 'AdminLogin'
    }
  }

  if (to.name === 'AdminLogin' && isAdminAuthenticated()) {
    return {
      name: 'AdminDashboard'
    }
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} - 超级智能` : '超级智能'
})

export default router
