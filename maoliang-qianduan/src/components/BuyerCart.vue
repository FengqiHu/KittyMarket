<template>
<div class="section-heading"><div><h1>我的购物车</h1><p>确认商品数量和金额后前往结算。</p></div><el-button @click="$router.push('/buyer')">返回商品列表<el-icon><IconArrowRight /></el-icon></el-button></div><section class="surface table-surface" v-loading="loading"><el-alert v-if="error" :title="error" type="error" class="form-alert" :closable="false" /><el-table :data="items" empty-text="购物车暂无商品"><el-table-column label="商品" min-width="250"><template #default="{row}"><div class="table-product"><ProductMedia :path="row.mediafiles" :alt="row.name" /><div><strong>{{ row.name }}</strong><span>{{ row.description }}</span><small>¥{{ Number(row.price).toFixed(2) }} / 件</small></div></div></template></el-table-column><el-table-column label="数量" min-width="160"><template #default="{row}"><el-input-number v-model="row.quantity" :min="1" :max="Math.max(1,row.maxquantity)" :precision="0" size="small" /></template></el-table-column><el-table-column label="小计" width="130"><template #default="{row}"><strong>¥{{ (row.price * row.quantity).toFixed(2) }}</strong></template></el-table-column><el-table-column label="操作" width="210"><template #default="{row}"><el-button type="primary" size="small" :disabled="row.maxquantity < 1" @click="checkout(row)">去结算</el-button><el-button link type="primary" @click="favorite(row)">收藏</el-button><el-button link type="danger" @click="remove(row)">移除</el-button></template></el-table-column></el-table><div class="table-pagination"><span class="muted">共 {{ items.length }} 件商品 · 在结算页确认购买数量与地址</span><strong>合计 <span class="product-price"><small>¥</small>{{ total.toFixed(2) }}</span></strong></div></section>
</template>
<script>
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
const normalize = row => Object.fromEntries(Object.entries(row).map(([key,value]) => [key.toLowerCase(),value]));
export default {
 data: () => ({ items: [], user: null, loading: false, error: '' }),
 computed: { total() { return this.items.reduce((sum,item) => sum + item.price * item.quantity,0); } },
 created() { this.load(); },
 methods: {
  async load() { this.loading = true; this.error = ''; try { this.user = (await axios.get('/now-usr')).data; if (!this.user) { this.error = '登录后即可查看购物车'; this.items = []; return; } const [cart,detail] = await Promise.all([axios.get('/cart/allCart',{params:{userId:this.user.userid}}),axios.get('/cart/buyer-cart-control')]); this.items = (cart.data || []).map(normalize).map(item => ({...item, quantity: Math.max(1,(detail.data.data || []).find(good => good.goodid === item.id)?.number || 1)})); } catch { this.error = '购物车加载失败，请稍后重试'; } finally { this.loading = false; } },
  async checkout(item) { try { await axios.post('/good/buyer-show-good/'+item.id); this.$router.push({name:'BuyerFillInfo',query:{goodid:item.id,number:item.quantity,fromCart:'1'}}); } catch { ElMessage.error('商品暂时无法结算，请稍后重试'); } },
  async favorite(item) { try { await axios.post('/cart/addLike',null,{params:{goodid:item.id,iscancel:0,userid:this.user.userid}}); ElMessage.success('已加入收藏'); } catch { ElMessage.error('收藏失败，请重试'); } },
  async remove(item) { try { await ElMessageBox.confirm('从购物车移除这件商品？','移除商品',{confirmButtonText:'移除',cancelButtonText:'保留',type:'warning'}); } catch { return; } try { await axios.post('/cart/delete-cart-item',null,{params:{buyingid:item.id,userid:this.user.userid}}); await this.load(); ElMessage.success('已移除'); } catch { ElMessage.error('移除失败，请重试'); } },
 },
};
</script>
