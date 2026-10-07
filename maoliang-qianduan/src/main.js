import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import store from './store';
import axios from "axios";
import { ElAlert, ElAvatar, ElButton, ElConfigProvider, ElDescriptions, ElDescriptionsItem, ElDialog, ElDivider, ElEmpty, ElForm, ElFormItem, ElIcon, ElImage, ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElPagination, ElRadioButton, ElRadioGroup, ElResult, ElSelect, ElSkeleton, ElTable, ElTableColumn, ElTag, ElLoading } from 'element-plus';
import 'element-plus/es/components/base/style/css';
import 'element-plus/es/components/alert/style/css';
import 'element-plus/es/components/avatar/style/css';
import 'element-plus/es/components/button/style/css';
import 'element-plus/es/components/descriptions/style/css';
import 'element-plus/es/components/dialog/style/css';
import 'element-plus/es/components/divider/style/css';
import 'element-plus/es/components/empty/style/css';
import 'element-plus/es/components/form/style/css';
import 'element-plus/es/components/icon/style/css';
import 'element-plus/es/components/image/style/css';
import 'element-plus/es/components/input/style/css';
import 'element-plus/es/components/input-number/style/css';
import 'element-plus/es/components/menu/style/css';
import 'element-plus/es/components/pagination/style/css';
import 'element-plus/es/components/radio-button/style/css';
import 'element-plus/es/components/radio-group/style/css';
import 'element-plus/es/components/result/style/css';
import 'element-plus/es/components/select/style/css';
import 'element-plus/es/components/skeleton/style/css';
import 'element-plus/es/components/table/style/css';
import 'element-plus/es/components/tag/style/css';
import 'element-plus/es/components/loading/style/css';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import './styles/theme.css';
import { House, Shop, ShoppingCart, Star, Tickets, User, Plus, Search, ArrowRight, ArrowLeft, SwitchButton, Lock, Box, Upload, Grid, Collection, Edit, Delete, Picture, Menu, Close, Check, Refresh, ArrowDown } from '@element-plus/icons-vue';
import AuthLayout from './components/AuthLayout.vue';
import ProductMedia from './components/ProductMedia.vue';

const app = createApp(App);
[ElAlert, ElAvatar, ElButton, ElConfigProvider, ElDescriptions, ElDescriptionsItem, ElDialog, ElDivider, ElEmpty, ElForm, ElFormItem, ElIcon, ElImage, ElInput, ElInputNumber, ElMenu, ElMenuItem, ElOption, ElPagination, ElRadioButton, ElRadioGroup, ElResult, ElSelect, ElSkeleton, ElTable, ElTableColumn, ElTag].forEach(component => app.use(component));
app.use(ElLoading);
app.component('AuthLayout', AuthLayout);
app.component('ProductMedia', ProductMedia);
Object.entries({ House, Shop, ShoppingCart, Star, Tickets, User, Plus, Search, ArrowRight, ArrowLeft, SwitchButton, Lock, Box, Upload, Grid, Collection, Edit, Delete, Picture, Menu, Close, Check, Refresh, ArrowDown }).forEach(([name, icon]) => app.component(`Icon${name}`, icon));

// 检查本地存储并初始化store的state
const adminData = localStorage.getItem('admin');
if (adminData) {
  try { const { userid, username, power } = JSON.parse(adminData); store.commit('setAdmin', { userid, username, power }); } catch { store.commit('clearAdmin'); }
}

// 设置基本的 URL
axios.defaults.baseURL = process.env.NODE_ENV === 'development' ? '/' : 'http://localhost:8080/';

// 添加 Axios 请求拦截器
axios.interceptors.request.use(function (config) {
  // 在发送请求之前做些什么
  // 判断请求目标地址是否包含/api，如果不包含则添加/api前缀
  if (!config.url.includes('/api')) {
    config.url = `/api${config.url}`;
  }
  return config;
}, function (error) {
  // 对请求错误做些什么
  return Promise.reject(error);
});

// 添加全局 Fetch 请求拦截器
const originalFetch = window.fetch;
window.fetch = function (url, options) {
  // 判断请求目标地址是否包含/api，如果不包含则添加/api前缀
  const apiUrl = !url.includes('/api') ? `/api${url}` : url;
  return originalFetch(apiUrl, options);
};

app.use(router);
app.use(store);
app.mount('#app');
