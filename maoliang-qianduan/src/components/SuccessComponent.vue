<template>
<section class="surface result-surface"><el-result icon="success" title="操作已完成" :sub-title="SuccessMessage"><template #extra><el-button type="primary" @click="$router.push(redirectTo)">继续</el-button></template></el-result></section>
</template>

<script>

export default {
  data() {
    return {
      SuccessMessage: '',
      redirectTo: null
    };
  },
  created() {
    // 从路由参数中获取错误信息和重定向路径
    this.SuccessMessage = this.$route.query.message || '操作成功';
    this.redirectTo = this.$route.query.to || '/';
  }
};
</script>
