<template>
  <div class="section-heading">
    <div><h1>确认订单</h1><p>确认收货地址，再核对订单商品。</p></div>
    <el-button @click="$router.push(fromCart ? '/cart' : '/buyer')">{{ fromCart ? '返回购物车' : '返回商品列表' }}</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
  <el-skeleton v-if="loading" :rows="5" animated class="surface" />
  <el-form v-else-if="products.length" class="checkout-layout" label-position="top" size="large" @submit.prevent="confirmPurchase">
    <section class="surface checkout-address" aria-labelledby="checkout-address-title">
      <div class="checkout-section-heading"><el-icon><IconHouse /></el-icon><h2 id="checkout-address-title">收货地址</h2></div>
      <el-alert v-if="!currentUser" title="请登录后提交订单" type="info" :closable="false" class="form-alert">
        <el-button text @click="$router.push('/login')">去登录</el-button>
      </el-alert>
      <AddressBook v-if="currentUser" selectable :disabled="submitting || submitted" @select="selectAddress" @busy="addressLoading = $event" />
    </section>
    <section class="surface checkout-products" aria-labelledby="checkout-products-title">
      <div class="checkout-section-heading"><el-icon><IconTickets /></el-icon><h2 id="checkout-products-title">订单商品</h2><span class="muted">{{ products.length }} 种商品 / {{ totalQuantity }} 件</span></div>
      <div class="checkout-product-labels" aria-hidden="true"><span>商品信息</span><span>购买数量</span><span>小计</span></div>
      <article v-for="item in products" :key="item.goodid" class="checkout-item">
        <div class="table-product checkout-product-info">
          <ProductMedia :path="item.picture" :alt="item.goodname" />
          <div><strong>{{ item.goodname }}</strong><small>单价 ¥{{ Number(item.price).toFixed(2) }} · 库存 {{ item.number }} 件</small><small v-if="item.state !== 0 || item.number < 1">商品已下架或售罄，请返回重新选择。</small></div>
        </div>
        <el-input-number v-model="item.quantity" class="checkout-quantity" :min="1" :max="Math.max(1,item.number)" :precision="0" :disabled="submitting || submitted" size="small" :aria-label="item.goodname + '购买数量'" />
        <strong class="checkout-subtotal">¥{{ (item.price * (item.quantity || 0)).toFixed(2) }}</strong>
      </article>
      <div class="checkout-summary">
        <span class="muted">共 {{ totalQuantity }} 件商品</span>
        <div class="checkout-submit">
          <strong>合计 <span class="product-price"><small>¥</small>{{ total }}</span></strong>
          <el-button type="primary" native-type="submit" size="large" :loading="submitting" :disabled="!currentUser || addressLoading || !selectedAddress || hasUnavailable || submitted">
            {{ submitted ? '订单已提交' : fromCart ? '合并提交订单' : '提交订单' }}
          </el-button>
        </div>
      </div>
    </section>
  </el-form>
  <el-empty v-else description="没有可结算的商品"><el-button @click="$router.push(fromCart ? '/cart' : '/buyer')">{{ fromCart ? '返回购物车' : '浏览商品' }}</el-button></el-empty>
</template>
<script>
import { ElMessage } from 'element-plus';
import axios from 'axios';
import AddressBook from './AddressBook.vue';

export default {
  components: { AddressBook },
  data: () => ({
    products: [], currentUser: null, selectedAddress: null, addressLoading: true, loading: true, submitting: false, submitted: false, error: '',
    purchase: { buyerName: '', telephone: '', address: '' },
  }),
  computed: {
    fromCart() { return this.$route.query.fromCart === '1'; },
    total() { return (this.products.reduce((sum,item) => sum + Math.round(Number(item.price) * 100) * (item.quantity || 0),0) / 100).toFixed(2); },
    totalQuantity() { return this.products.reduce((sum,item) => sum + (item.quantity || 0),0); },
    hasUnavailable() { return this.products.some(item => item.state !== 0 || item.number < 1); },
  },
  created() { this.loadCheckout(); },
  methods: {
    async loadCheckout() {
      this.loading = true; this.error = '';
      try {
        let selection;
        if (this.fromCart && this.$route.query.items) {
          selection = JSON.parse(this.$route.query.items);
          if (!Array.isArray(selection) || !selection.length || selection.some(item => !item || !Number.isInteger(item.goodid) || item.goodid < 1 || !Number.isInteger(item.number) || item.number < 1)
              || new Set(selection.map(item => item.goodid)).size !== selection.length) throw new Error('Invalid selection');
        } else {
          const goodid = Number(this.$route.query.goodid);
          const quantity = Number(this.$route.query.number);
          selection = [{ goodid: goodid > 0 ? goodid : null, number: Number.isInteger(quantity) && quantity > 0 ? quantity : 1 }];
        }
        const [products, user] = await Promise.all([
          Promise.all(selection.map(async item => {
            const { data } = await axios.get(item.goodid ? '/good/details/' + item.goodid : '/now-good');
            if (!data?.goodid) throw new Error('Product unavailable');
            return { ...data, quantity: item.number };
          })),
          axios.get('/now-usr'),
        ]);
        this.products = products;
        this.currentUser = user.data?.userid ? user.data : null;
        if (this.currentUser) {
          this.purchase.buyerName = this.currentUser.username || '';
          this.purchase.telephone = this.currentUser.phone || '';
          this.purchase.address = this.currentUser.address || '';
        }
        if (this.hasUnavailable) this.error = '部分商品已下架或售罄，请返回重新选择。';
      } catch {
        this.products = [];
        this.error = '结算信息加载失败，请返回重新选择商品。';
      } finally { this.loading = false; }
    },
    selectAddress(address) {
      this.selectedAddress = address;
      this.purchase = { buyerName: address?.recipient || '', telephone: address?.telephone || '', address: address?.address || '' };
    },
    validateForm() {
      if (!this.currentUser) return '请先登录后再下单';
      if (this.addressLoading) return '地址正在保存或加载，请稍候';
      if (!this.selectedAddress) return '请添加并选择收货地址';
      if (!this.products.length || this.hasUnavailable) return '请选择有效的商品';
      if (this.products.some(item => !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > item.number)) return '购买数量需要为正整数且不能超过剩余库存';
      if (!this.purchase.buyerName.trim() || this.purchase.buyerName.trim().length > 10) return '请填写 10 个字符以内的收货人姓名';
      if (!/^[0-9]{11}$/.test(this.purchase.telephone.trim())) return '联系电话需要是 11 位数字';
      if (!this.purchase.address.trim() || this.purchase.address.trim().length > 99) return '请填写 99 个字符以内的收货地址';
      return '';
    },
    async confirmPurchase() {
      if (this.submitting || this.submitted) return;
      this.error = this.validateForm();
      if (this.error) return;
      this.submitting = true;
      try {
        const items = this.products.map(item => ({ goodid: item.goodid, number: item.quantity }));
        const { data } = await axios.post(this.fromCart ? '/order/checkout-cart' : '/order/createorder-control', {
          ...this.purchase, telephone: this.purchase.telephone.trim(),
          ...(this.fromCart ? { items } : { ...items[0], fromCart: false }),
        });
        const orders = this.fromCart ? data.data : [data.data];
        if (data.page === '/error' || !Array.isArray(orders) || orders.length !== items.length || orders.some(order => !order?.orderid)) {
          this.error = data.msg || '下单失败，请重试'; return;
        }
        this.submitted = true;
        ElMessage.success(this.fromCart ? '所选商品已全部结算' : '订单 #' + orders[0].orderid + ' 已提交');
        this.$router.push({ path: '/buyer-history', query: this.fromCart
          ? { orderids: orders.map(order => order.orderid).join(',') }
          : { orderid: orders[0].orderid } });
      } catch {
        this.error = '提交失败，请检查“我的订单”确认是否已创建订单。';
      } finally { this.submitting = false; }
    },
  },
};
</script>
<style scoped>
.checkout-layout { max-width: 1040px; margin: 0 auto; }
.checkout-section-heading { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; }
.checkout-section-heading > .el-icon { color: var(--orange); font-size: 20px; }
.checkout-section-heading h2 { margin: 0; font-size: 18px; font-weight: 600; }
.checkout-section-heading > .muted { margin-left: auto; }
.checkout-address-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 0 24px; }
.checkout-full-row { grid-column: 1 / -1; margin-bottom: 0; }
.checkout-product-labels, .checkout-item { display: grid; grid-template-columns: minmax(0, 1fr) 160px 120px; align-items: center; gap: 24px; }
.checkout-product-labels { padding: 12px 0; color: var(--muted); font-size: 12px; border-bottom: 1px solid var(--line); }
.checkout-product-labels > :last-child, .checkout-subtotal { text-align: right; }
.checkout-item { padding: 20px 0; border-bottom: 1px solid var(--line); }
.checkout-product-info { min-width: 0; }
.checkout-product-info > div { min-width: 0; }
.checkout-product-info strong { overflow-wrap: anywhere; line-height: 1.6; }
.checkout-product-info .product-media { width: 72px; height: 72px; flex-basis: 72px; }
.checkout-quantity { width: 160px; }
.checkout-summary { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 20px; padding-top: 24px; }
.checkout-submit { display: flex; align-items: center; flex-wrap: wrap; gap: 28px; }
.checkout-submit > strong { white-space: nowrap; }
.checkout-submit .el-button { min-width: 180px; }
@media (max-width: 640px) {
  .checkout-address-fields { grid-template-columns: 1fr; }
  .checkout-section-heading { flex-wrap: wrap; gap: 8px; }
  .checkout-section-heading > .muted { margin-left: 0; flex-basis: 100%; }
  .checkout-product-labels { display: none; }
  .checkout-item { grid-template-columns: minmax(0, 1fr) auto; gap: 14px; }
  .checkout-product-info { grid-column: 1 / -1; }
  .checkout-quantity { width: 140px; }
  .checkout-subtotal { font-size: 16px; }
  .checkout-submit { width: 100%; justify-content: space-between; gap: 16px; }
  .checkout-submit .el-button { min-width: 0; }
}
</style>
