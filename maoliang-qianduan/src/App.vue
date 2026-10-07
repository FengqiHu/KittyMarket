<template>
<el-config-provider :locale="zhCn">
  <router-view v-if="isAuthPage" />
  <div v-else class="app-layout" :class="isSeller ? 'seller-layout' : 'buyer-layout'">
    <aside class="app-sidebar" :class="{ 'is-open': menuOpen }">
      <router-link :to="isSeller ? '/seller' : '/buyer'" class="brand"><span class="brand-mark">猫</span><span>猫咪美食坊</span></router-link>
      <div class="sidebar-label">{{ isSeller ? '商家后台' : '商城导航' }}</div>
      <el-menu router :default-active="$route.path" @select="menuOpen = false"><el-menu-item v-for="item in navigation" :key="item.path" :index="item.path"><el-icon><component :is="'Icon' + item.icon" /></el-icon><span>{{ item.label }}</span></el-menu-item></el-menu>
      <router-link class="sidebar-switch" :to="isSeller ? '/buyer' : '/'"><el-icon><IconShop /></el-icon>{{ isSeller ? '前往商城' : '返回登录页' }}<el-icon><IconArrowRight /></el-icon></router-link>
    </aside>
    <div v-if="menuOpen" class="sidebar-backdrop" @click="menuOpen = false"></div>
    <div class="app-workspace">
      <header class="app-header">
        <el-button class="mobile-menu" text aria-label="打开导航菜单" @click="menuOpen = !menuOpen"><el-icon><IconMenu /></el-icon></el-button>
        <router-link v-if="!isSeller" to="/buyer" class="brand header-brand"><span class="brand-mark">猫</span><span>猫咪美食坊</span></router-link>
        <div v-else class="header-context"><span>商家后台</span><span class="header-divider">/</span><strong>{{ pageTitle }}</strong></div>
        <div class="header-account"><span class="account-role">{{ user ? (user.power === 1 ? '商家' : '会员') : '游客' }}</span><el-avatar :size="30">{{ user ? user.username.slice(0, 1) : '客' }}</el-avatar><strong>{{ user ? user.username : '未登录' }}</strong><el-button v-if="user" text @click="logout"><el-icon><IconSwitchButton /></el-icon><span class="logout-label">退出</span></el-button><el-button v-else type="primary" @click="$router.push('/')">登录</el-button></div>
      </header>
      <nav v-if="!isSeller" class="store-navigation" aria-label="商城导航"><router-link v-for="item in buyerNavigation" :key="item.path" :to="item.path" :class="{ 'is-active': $route.path === item.path }">{{ item.label }}</router-link><router-link v-if="user && user.power === 1" to="/seller" class="merchant-entry">商家后台</router-link></nav>
      <main class="page-content" :class="{ 'seller-content': isSeller }"><router-view /></main>
      <footer class="app-footer"><span>猫咪美食坊</span><span>© {{ new Date().getFullYear() }}</span></footer>
    </div>
  </div>
</el-config-provider>
</template>
<script>
import axios from 'axios';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { ElMessage } from 'element-plus';
const buyerNav = [
  { path: '/buyer', label: '全部商品', icon: 'House' }, { path: '/cart', label: '购物车', icon: 'ShoppingCart' }, { path: '/likes', label: '我的收藏', icon: 'Star' }, { path: '/buyer-history', label: '我的订单', icon: 'Tickets' }, { path: '/buyer-show-recommend', label: '猫粮推荐', icon: 'Collection' }, { path: '/buyer-show-cat', label: '猫咪档案', icon: 'User' },
];
const sellerNav = [
  { path: '/seller/ShowGoods', label: '商品管理', icon: 'Grid' }, { path: '/seller/upload-onegood', label: '发布商品', icon: 'Plus' }, { path: '/seller/upload-multiplegoods', label: '批量发布', icon: 'Upload' }, { path: '/seller/show-userinfo', label: '订单管理', icon: 'Tickets' }, { path: '/seller/show-historygoods', label: '商品历史', icon: 'Box' }, { path: '/seller/show-allusers', label: '客户管理', icon: 'User' }, { path: '/seller/update-password', label: '账户设置', icon: 'Lock' },
];
export default {
  data: () => ({ user: null, menuOpen: false, zhCn }),
  computed: {
    isAuthPage() { return ['/', '/login', '/register', '/choose-register', '/forgot-password', '/secret-question'].includes(this.$route.path); },
    isSeller() { return this.$route.path.startsWith('/seller'); },
    navigation() { return this.isSeller ? sellerNav : buyerNav; },
    buyerNavigation() { return buyerNav; },
    pageTitle() { return [...buyerNav, ...sellerNav].find(item => item.path === this.$route.path)?.label || ({ '/buyer-shop': '商品详情', '/buyer-search': '搜索结果', '/buyer-fill-info': '确认订单', '/success': '操作完成', '/error': '操作提示' }[this.$route.path]) || '猫咪美食坊'; },
  },
  watch: { '$route.path': { immediate: true, handler() { this.refreshSession(); this.menuOpen = false; } } },
  methods: {
    async refreshSession() { try { const { data } = await axios.get('/now-usr'); this.user = data || null; if (data) { const { userid, username, power } = data; this.$store.commit('setAdmin', { userid, username, power }); } else this.$store.commit('clearAdmin'); } catch { this.user = null; } },
    async logout() { try { await axios.get('/logout-control'); this.user = null; this.$store.commit('clearAdmin'); this.$router.push('/'); } catch { ElMessage.error('退出失败，请重试'); } },
  },
};
</script>
