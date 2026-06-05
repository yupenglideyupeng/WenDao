import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login']

router.beforeEach(async (to, from) => {
  NProgress.start()

  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 闻道数据大屏`
  }

  if (getToken()) {
    // 已登录
    if (to.path === '/login') {
      NProgress.done()
      return { path: '/dashboard' }
    }
    // 验证 token 有效性
    if (useUserStore().roles.length === 0) {
      try {
        await useUserStore().getInfo()
        return true
      } catch (err) {
        await useUserStore().logOut()
        NProgress.done()
        return `/login?redirect=${to.fullPath}`
      }
    }
    return true
  } else {
    // 未登录
    if (whiteList.includes(to.path)) {
      return true
    }
    NProgress.done()
    return `/login?redirect=${to.fullPath}`
  }
})

router.afterEach(() => {
  NProgress.done()
})
