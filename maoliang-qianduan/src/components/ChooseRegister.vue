<template>
<AuthLayout><h2 class="auth-title">选择账号类型</h2><p class="auth-subtitle">买家用于购物，商家用于商品和订单管理。</p><div class="role-choices"><el-button size="large" @click="$router.push('/register?registeruser=0')"><el-icon><IconShoppingCart /></el-icon>注册买家账号</el-button><el-button size="large" @click="$router.push('/register?registeruser=1')"><el-icon><IconShop /></el-icon>注册商家账号</el-button></div><div class="auth-register"><router-link to="/">返回登录</router-link></div></AuthLayout>
</template>

<script>
export default {
  data() {
    return {
      error: ''
    };
  }
};
</script>
