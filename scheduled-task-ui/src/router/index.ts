import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import routes from './routes'

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const whiteList = ['/login']

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  if (userStore.token) {
    if (!userStore.userInfo) {
      try {
        await userStore.fetchCurrentUser()
      } catch {
        userStore.logout()
        return next('/login')
      }
    }
    if (to.path === '/login') return next('/')
    return next()
  }
  if (whiteList.includes(to.path)) return next()
  return next('/login')
})

export default router
