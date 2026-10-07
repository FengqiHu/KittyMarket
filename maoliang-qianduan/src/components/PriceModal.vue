<template>
<el-dialog :model-value="isVisible" title="调整商品价格" width="420px" align-center @close="closeModal"><template v-if="good"><p class="muted">{{ good.goodname }} · 当前价格 ¥{{ Number(good.price).toFixed(2) }}</p><el-form label-position="top" @submit.prevent="updatePrice"><el-form-item label="新价格（元）"><el-input-number v-model="newPrice" :min="0.01" :precision="2" :step="1" controls-position="right" /></el-form-item></el-form></template><template #footer><el-button @click="closeModal">取消</el-button><el-button type="primary" :disabled="!newPrice || newPrice <= 0" @click="updatePrice">确认修改</el-button></template></el-dialog>
</template>

<script>
export default {
  props: {
    good: Object,
    isVisible: Boolean,
  },
data() {
return {
newPrice: undefined, // 新价格
};
},
watch: { isVisible(value) { if (value) this.newPrice = this.good?.price; } },
methods: {
closeModal() {
this.$emit('close'); // 触发父组件的关闭事件
},
updatePrice() {
// 这里添加更新价格的逻辑
// 通常是发起一个API请求，然后通知父组件更新
this.$emit('update', { goodId: this.good.goodid, newPrice: this.newPrice });
},
},
};
</script>
