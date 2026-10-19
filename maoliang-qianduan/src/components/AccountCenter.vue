<template>
  <div class="section-heading"><div><h1>个人管理</h1><p>管理账号、收货地址和购物记录。</p></div><el-button @click="$router.push('/buyer')">返回商城</el-button></div>
  <el-skeleton v-if="loading" :rows="5" animated class="surface" />
  <el-alert v-else-if="error" :title="error" type="error" :closable="false" class="form-alert"><el-button @click="$router.push('/login')">前往登录</el-button></el-alert>
  <template v-else-if="user">
    <section class="surface account-overview"><el-avatar :size="64">{{ user.username.slice(0, 1) }}</el-avatar><div><h2>{{ user.username }}</h2><p class="muted">{{ user.power === 1 ? '商家账号' : '会员账号' }}</p></div><router-link to="/cart" class="account-cart-link"><el-icon><IconShoppingCart /></el-icon>我的购物车<el-icon><IconArrowRight /></el-icon></router-link></section>
    <nav class="account-tabs" aria-label="个人管理功能"><button v-for="item in tabs" :key="item.id" :class="{ 'is-active': activeTab === item.id }" :aria-current="activeTab === item.id ? 'page' : undefined" @click="$router.replace({ path: '/account', query: { tab: item.id } })"><el-icon><component :is="'Icon' + item.icon" /></el-icon>{{ item.label }}</button></nav>
    <section v-if="activeTab === 'profile'" class="surface">
      <h2 class="account-section-title">账号信息</h2>
      <el-form class="account-settings-form" label-position="top" size="large" @submit.prevent="saveName">
        <el-form-item label="账号名称"><el-input v-model="username" required maxlength="10" placeholder="1–10 个字符，用于登录" :disabled="savingName" /></el-form-item>
        <p class="muted">修改后使用新名称登录，已有订单和收藏会保留。</p>
        <el-alert v-if="nameError" :title="nameError" type="error" :closable="false" class="form-alert" />
        <el-button native-type="submit" type="primary" :loading="savingName" :disabled="username.trim() === user.username">保存账号名称</el-button>
      </el-form>
    </section>
    <section v-else-if="activeTab === 'addresses'" class="surface"><h2 class="account-section-title">收货地址</h2><AddressBook /></section>
    <BuyerHistory v-else-if="activeTab === 'orders'" />
    <BuyerLikes v-else-if="activeTab === 'favorites'" />
    <section v-else-if="activeTab === 'password'" class="surface">
      <h2 class="account-section-title">密码管理</h2>
      <el-form class="account-settings-form" label-position="top" size="large" @submit.prevent="savePassword">
        <el-form-item label="当前密码"><el-input v-model="password.currentPassword" type="password" show-password required autocomplete="current-password" :disabled="savingPassword" /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="password.newPassword" type="password" show-password required minlength="8" maxlength="64" placeholder="8–64 个字符" autocomplete="new-password" :disabled="savingPassword" /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="password.confirmPassword" type="password" show-password required minlength="8" maxlength="64" autocomplete="new-password" :disabled="savingPassword" /></el-form-item>
        <el-alert v-if="passwordError" :title="passwordError" type="error" :closable="false" class="form-alert" />
        <el-button native-type="submit" type="primary" :loading="savingPassword">保存新密码</el-button>
      </el-form>
    </section>
  </template>
</template>
<script>
import { ElMessage } from 'element-plus';
import { accountRequest } from '../utils/accountApi';
import AddressBook from './AddressBook.vue';
import BuyerHistory from './BuyerHistory.vue';
import BuyerLikes from './BuyerLikes.vue';
export default {
  components: { AddressBook, BuyerHistory, BuyerLikes },
  emits: ['profile-updated'],
  data: () => ({
    user: null, username: '', loading: true, error: '', nameError: '', passwordError: '', savingName: false, savingPassword: false,
    password: { currentPassword: '', newPassword: '', confirmPassword: '' },
    tabs: [{ id: 'profile', label: '账号信息', icon: 'User' }, { id: 'addresses', label: '收货地址', icon: 'House' }, { id: 'orders', label: '历史订单', icon: 'Tickets' }, { id: 'favorites', label: '我的收藏', icon: 'Star' }, { id: 'password', label: '密码管理', icon: 'Lock' }],
  }),
  computed: { activeTab() { return this.tabs.some(tab => tab.id === this.$route.query.tab) ? this.$route.query.tab : 'profile'; } },
  async created() { try { this.user = await accountRequest('get'); this.username = this.user.username; } catch (error) { this.error = error.message; } finally { this.loading = false; } },
  methods: {
    async saveName() {
      if (this.savingName) return;
      this.savingName = true; this.nameError = '';
      try {
        this.user = await accountRequest('put', '/name', { username: this.username });
        this.username = this.user.username;
        this.$store.commit('setAdmin', { userid: this.user.userid, username: this.user.username, power: this.user.power });
        this.$emit('profile-updated');
        ElMessage.success('账号名称已更新');
      } catch (error) { this.nameError = error.message; }
      finally { this.savingName = false; }
    },
    async savePassword() {
      if (this.savingPassword) return;
      this.passwordError = '';
      if (this.password.newPassword !== this.password.confirmPassword) { this.passwordError = '两次输入的新密码不一致'; return; }
      this.savingPassword = true;
      try { await accountRequest('put', '/password', this.password); this.password = { currentPassword: '', newPassword: '', confirmPassword: '' }; ElMessage.success('密码已更新，下次登录请使用新密码'); }
      catch (error) { this.passwordError = error.message; }
      finally { this.savingPassword = false; }
    },
  },
};
</script>
<style scoped>
.account-overview { display: flex; align-items: center; flex-wrap: wrap; gap: 20px; }
.account-overview .el-avatar { background: var(--el-color-primary-light-8); color: var(--orange); font-size: 26px; }
.account-overview h2 { margin-bottom: 6px; font-size: 22px; }
.account-overview p { margin-bottom: 0; }
.account-cart-link { display: inline-flex; align-items: center; gap: 10px; margin-left: auto; }
.account-tabs { display: flex; gap: 8px; overflow-x: auto; white-space: nowrap; margin: 24px 0; border-bottom: 1px solid var(--line); }
.account-tabs button { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; background: transparent; border: 0; border-bottom: 2px solid transparent; padding: 15px 18px; color: var(--muted); }
.account-tabs button.is-active { color: var(--orange); border-bottom-color: var(--orange); font-weight: 600; }
.account-section-title { font-size: 18px; margin-bottom: 24px; }
.account-settings-form { max-width: 480px; }
@media (max-width: 640px) { .account-tabs button { padding: 14px 10px; } .account-cart-link { margin-left: 0; } }
</style>
