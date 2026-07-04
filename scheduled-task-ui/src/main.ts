import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'
import AppBreadcrumb from './components/AppBreadcrumb.vue'
import BasePagination from './components/BasePagination.vue'
import BaseSearchForm from './components/BaseSearchForm.vue'
import './styles/index.scss'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.component('AppBreadcrumb', AppBreadcrumb)
app.component('BasePagination', BasePagination)
app.component('BaseSearchForm', BaseSearchForm)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn, zIndex: 3000 })
app.directive('permission', permission)

app.mount('#app')
