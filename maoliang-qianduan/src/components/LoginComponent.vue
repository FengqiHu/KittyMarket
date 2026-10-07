<template>
<AuthLayout><h2 class="auth-title">账号登录</h2><p class="auth-subtitle">买家和商家使用同一入口登录。</p>
<el-form v-if="!isLoggedIn" label-position="top" size="large" @submit.prevent="handleLogin">
  <el-form-item label="账号"><el-input v-model="credentials.username" placeholder="请输入你的账号" autocomplete="username" name="username" maxlength="10"><template #prefix><el-icon><IconUser /></el-icon></template></el-input></el-form-item>
  <el-form-item label="密码"><el-input v-model="credentials.password" type="password" show-password placeholder="请输入密码" autocomplete="current-password" name="password"><template #prefix><el-icon><IconLock /></el-icon></template></el-input></el-form-item>
  <div class="auth-options"><router-link to="/forgot-password">忘记密码？</router-link></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
  <el-button type="primary" native-type="submit" class="full-width" :loading="loading">登录<el-icon class="icon-after"><IconArrowRight /></el-icon></el-button>
  <div class="auth-register">还没有账号？<router-link to="/register">立即注册</router-link></div>
  <el-divider>游客入口</el-divider><el-button class="full-width" @click="$router.push('/buyer')">浏览商品<el-icon class="icon-after"><IconShop /></el-icon></el-button>
</el-form>
<el-result v-else icon="success" title="你已登录" sub-title="请选择进入商城或商家后台。"><template #extra><el-button type="primary" @click="$router.push(adminRoute)">进入{{ isSeller ? '商家工作台' : '商城' }}</el-button><el-button @click="handleLogout">切换账号</el-button></template></el-result>
</AuthLayout>
</template>

<script>
import axios from 'axios';


export default {
  data() {
    return {
      credentials: {
        username: '',
        password: ''
      },
      error: '',
      loading: false,
      mockAdminData: null,
      currentUser :null
    };
  },

  created() {
    this.fetchUsrFromSession();
  },
  computed: {
    isLoggedIn() {
      // 根据当前用户数据判断用户是否登录
      return !!this.currentUser;
    },
    isSeller() {
      // 根据当前用户数据判断用户是否是卖家
      return this.currentUser && this.currentUser.power === 1;
    },
    // 判断用户是否是买家的方法
    isBuyer() {
      // 根据当前用户数据判断用户是否是买家
      return this.currentUser && this.currentUser.power === 0;
    },
    adminRoute() {
      // 根据用户权限返回相应的路由
      return this.isSeller ? '/seller' : '/buyer';
    }

  },
  methods: {
    async fetchUsrFromSession() {
      try {
        // 发起 GET 请求到后端接口
        const response = await axios.get('/now-usr');
        // 解析响应数据
        const usr = response.data;
        // 更新组件的 currentUser 数据
        this.currentUser = usr;

        return true;
      } catch (error) {
        console.error('获取用户数据错误:', error);
        return false;
      }
    },
    async handleLogin() {
      this.error = '';
      if (!this.credentials.username.trim() || !this.credentials.password) { this.error = '请填写账号和密码'; return; }
      this.loading = true;
      try {
        const credentials = {
          username: this.credentials.username,
          password: this.credentials.password
        };

        const response = await fetch('/usr/login-control', {
          method: 'POST',
          body: JSON.stringify(credentials),
          headers: {
            'Content-Type': 'application/json'
          }
        });

        const responseData = await response.json();

        if (responseData.page === '/error') {
          // 重定向到错误页面，并将错误消息和重定向目标作为参数传递
          this.error = responseData.msg;
        } else if (responseData.page === '/success') {
          // 重定向到成功界面，并将成功消息和重定向目标作为参数传递
          this.$router.push({ path: '/success', query: { message: responseData.msg, to: responseData.data }})
        } else if (responseData.page === null) {
          console.log("未知页面类型");
        } else {
          this.$router.push({ path: responseData.page });
        }
      } catch (error) {
        console.error("登录错误:", error);
        this.error = '登录失败，请检查连接后重试';
      } finally { this.loading = false; }
    },

    async handleLogout() {
      try {
        // 向后端发送登出请求
        await axios.get('/logout-control');
        // 跳转到指定路由
        this.$router.push('/');
      } catch (error) {
        console.error('登出失败:', error);
        // 可选：处理登出失败的情况
      }
    },
  },
}
</script>
