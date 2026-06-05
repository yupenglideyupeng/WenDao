import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/assets/styles/index.scss'
import './styles/screen.scss'

import App from './App.vue'
import router from './router'
import store from '@/store'
import './permission'

// 强制暗黑模式
document.documentElement.classList.add('dark')

const app = createApp(App)

app.use(ElementPlus, { locale: zhCn, size: 'default' })
app.use(store)
app.use(router)

app.mount('#app')
