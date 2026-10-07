<template>
<section class="surface result-surface"><el-result icon="warning" title="这一步没有完成" :sub-title="errorMessage || '请检查输入后再试一次。'"><template #extra><el-button type="primary" @click="$router.push(redirectTo)">返回重试</el-button><el-button @click="$router.back()">上一页</el-button></template></el-result></section>
</template>

<script>
export default {
  data() {
    return {
      errorMessage: '',
      redirectTo: null
    };
  },
  created() {
    // 从路由参数中获取错误信息和重定向路径
    this.errorMessage = this.$route.query.err || '';
    this.redirectTo = this.$route.query.to || '/';
  }
};
</script>
