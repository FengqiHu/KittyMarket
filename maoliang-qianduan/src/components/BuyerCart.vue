<template>
  <div class="section-heading">
    <div><h1>我的购物车</h1><p>勾选需要购买的商品，合并填写收货信息并结算。</p></div>
    <el-button @click="$router.push('/buyer')">返回商品列表<el-icon><IconArrowRight /></el-icon></el-button>
  </div>
  <section class="surface table-surface" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" class="form-alert" :closable="false" />
    <el-table :data="items" row-key="id" empty-text="购物车暂无商品" @selection-change="selectedItems = $event">
      <el-table-column type="selection" width="50" :selectable="canSelect" />
      <el-table-column label="商品" min-width="250">
        <template #default="{row}">
          <div class="table-product">
            <ProductMedia :path="row.mediafiles" :alt="row.name" />
            <div><strong>{{ row.name }}</strong><span>{{ row.description }}</span><small>¥{{ Number(row.price).toFixed(2) }} / 件</small><small v-if="!canSelect(row)">已下架或售罄</small></div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="数量" min-width="160">
        <template #default="{row}"><el-input-number v-model="row.quantity" :min="1" :max="Math.max(1,row.maxquantity)" :precision="0" :disabled="!canSelect(row)" size="small" /></template>
      </el-table-column>
      <el-table-column label="小计" width="130">
        <template #default="{row}"><strong>¥{{ (row.price * row.quantity).toFixed(2) }}</strong></template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{row}"><el-button :type="isFavorite(row.id) ? 'primary' : 'default'" :aria-pressed="isFavorite(row.id)" :title="isFavorite(row.id) ? '点击取消收藏' : '收藏商品'" :loading="favoriteBusy(row.id)" size="small" @click="toggleFavorite(row.id, user.userid)"><el-icon><IconStarFilled v-if="isFavorite(row.id)" /><IconStar v-else /></el-icon>{{ favoriteLabel(row.id) }}</el-button><el-button link type="danger" @click="remove(row)">移除</el-button></template>
      </el-table-column>
    </el-table>
    <div class="table-pagination cart-checkout-bar">
      <span class="muted">共 {{ items.length }} 种商品 · 已选 {{ selectedItems.length }} 种 / {{ selectedQuantity }} 件</span>
      <div class="cart-checkout-actions">
        <strong>所选合计 <span class="product-price"><small>¥</small>{{ total.toFixed(2) }}</span></strong>
        <el-button type="primary" size="large" :disabled="loading || !selectedItems.length" @click="checkout">结算所选（{{ selectedItems.length }}）</el-button>
      </div>
    </div>
  </section>
</template>
<script>
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import favorites from '../mixins/favorites';
const normalize = row => Object.fromEntries(Object.entries(row).map(([key,value]) => [key.toLowerCase(),value]));
export default {
  mixins: [favorites],
  data: () => ({ items: [], selectedItems: [], user: null, loading: false, error: '' }),
  computed: {
    total() { return this.selectedItems.reduce((sum,item) => sum + Math.round(item.price * 100) * (item.quantity || 0),0) / 100; },
    selectedQuantity() { return this.selectedItems.reduce((sum,item) => sum + (item.quantity || 0),0); },
  },
  created() { this.load(); },
  methods: {
    canSelect(item) { return Number(item.state) === 0 && item.maxquantity > 0; },
    async load() {
      this.loading = true; this.error = ''; this.selectedItems = [];
      try {
        this.user = (await axios.get('/now-usr')).data;
        if (!this.user?.userid) { this.error = '登录后即可查看购物车'; this.items = []; return; }
        await this.loadFavorites(this.user.userid);
        const { data } = await axios.get('/cart/allCart', { params: { userId: this.user.userid } });
        if (!Array.isArray(data)) throw new Error('购物车数据格式错误');
        this.items = data.map(normalize).map(item => ({ ...item, quantity: Math.max(1, Number(item.quantity) || 1) }));
      } catch { this.error = '购物车加载失败，请稍后重试'; }
      finally { this.loading = false; }
    },
    checkout() {
      if (!this.selectedItems.length) return;
      if (this.selectedItems.some(item => !this.canSelect(item) || !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > item.maxquantity)) {
        ElMessage.error('请检查所选商品的购买数量，不能超过剩余库存'); return;
      }
      this.$router.push({ name: 'BuyerFillInfo', query: {
        fromCart: '1', items: JSON.stringify(this.selectedItems.map(item => ({ goodid: item.id, number: item.quantity }))),
      } });
    },
    async remove(item) { try { await ElMessageBox.confirm('从购物车移除这件商品？','移除商品',{confirmButtonText:'移除',cancelButtonText:'保留',type:'warning'}); } catch { return; } try { await axios.post('/cart/delete-cart-item',null,{params:{buyingid:item.id,userid:this.user.userid}}); await this.load(); ElMessage.success('已移除'); } catch { ElMessage.error('移除失败，请重试'); } },
  },
};
</script>
<style scoped>
.cart-checkout-bar { flex-wrap: wrap; }
.cart-checkout-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 20px; }
</style>
