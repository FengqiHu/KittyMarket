<template>
<section class="surface"><el-result icon="info" title="正在打开猫咪档案" /></section>
</template>
<script>
export default {
  name: 'BuyerUploadCat',
  created() { this.$router.replace({ path: '/buyer-show-cat', query: { action: 'add' } }); }
};
</script>
