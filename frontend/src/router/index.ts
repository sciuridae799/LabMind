import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import { hasAnyRole, isAuthenticated, type AuthRole } from '../shared/auth/authSession'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/AdminLoginView.vue'),
    meta: {
      layout: 'fullscreen',
      title: '登录'
    }
  },
  {
    path: '/chat',
    name: 'BusinessChat',
    component: () => import('../views/BusinessChatView.vue'),
    meta: {
      requiresAuth: true,
      title: '实验室文档问答'
    }
  },
  {
    path: '/paper-graphs',
    name: 'PaperGraphs',
    component: () => import('../views/PaperGraphView.vue'),
    meta: {
      requiresAuth: true,
      title: '论文知识图谱'
    }
  },
  {
    path: '/model-config',
    name: 'ModelConfig',
    component: () => import('../views/ModelConfigView.vue'),
    meta: {
      requiresAuth: true,
      roles: ['super_admin'],
      title: '模型配置'
    }
  },
  {
    path: '/admin/login',
    redirect: '/login'
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayoutView.vue'),
    meta: {
      requiresAuth: true
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
          roles: ['super_admin'],
          title: '前端 API 目录'
        }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../views/admin/AdminUserManagementView.vue'),
        meta: {
          roles: ['super_admin'],
          title: '账号管理'
        }
      },
      {
        path: 'workspaces',
        name: 'AdminWorkspaces',
        component: () => import('../views/admin/AdminWorkspaceManagementView.vue'),
        meta: {
          roles: ['super_admin'],
          title: '工作组管理'
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
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return {
      name: 'Login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  const roles = Array.isArray(to.meta.roles) ? to.meta.roles as AuthRole[] : undefined
  if (roles && !hasAnyRole(roles)) {
    return {
      name: 'AdminDashboard'
    }
  }

  if (to.name === 'Login' && isAuthenticated()) {
    return {
      name: 'BusinessChat'
    }
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} - 实验室 AI 文档助手` : '实验室 AI 文档助手'
})

export default router
