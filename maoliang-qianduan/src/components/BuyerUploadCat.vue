<template>
<section class="surface"><el-result icon="info" title="猫咪档案" sub-title="当前版本支持查看和更新已有猫咪，新增档案功能尚未开放。"><template #extra><el-button type="primary" @click="$router.push('/buyer-show-cat')">查看我的猫咪</el-button></template></el-result></section>
</template>
<script>
export default {name:'BuyerUploadCat'};
</script>
