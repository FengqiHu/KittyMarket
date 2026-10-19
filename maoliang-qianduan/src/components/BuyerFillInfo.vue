<template>
  <div class="section-heading">
    <div><h1>确认订单</h1><p>核对所选商品、金额和收货信息。</p></div>
    <el-button @click="$router.push(fromCart ? '/cart' : '/buyer')">{{ fromCart ? '返回购物车' : '返回商品列表' }}</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
  <el-skeleton v-if="loading" :rows="5" animated class="surface" />
  <div v-else-if="products.length" class="checkout-layout">
    <section class="surface">
      <div v-for="item in products" :key="item.goodid" class="checkout-item">
        <div class="table-product">
          <ProductMedia :path="item.picture" :alt="item.goodname" />
          <div><strong>{{ item.goodname }}</strong><small>¥{{ Number(item.price).toFixed(2) }} / 件 · 库存 {{ item.number }} 件</small></div>
        </div>
        <div class="checkout-item-quantity">
          <el-input-number v-model="item.quantity" :min="1" :max="Math.max(1,item.number)" :precision="0" :disabled="submitting || submitted" size="small" :aria-label="item.goodname + '购买数量'" />
          <strong>¥{{ (item.price * (item.quantity || 0)).toFixed(2) }}</strong>
        </div>
        <p v-if="item.state !== 0 || item.number < 1" class="muted">商品已下架或售罄，请返回重新选择。</p>
      </div>
      <el-divider />
      <p class="muted">共 {{ products.length }} 种商品 / {{ totalQuantity }} 件</p>
      <strong>合计 <span class="product-price"><small>¥</small>{{ total }}</span></strong>
    </section>
    <section class="surface">
      <el-alert v-if="!currentUser" title="请登录后提交订单" type="info" :closable="false" class="form-alert">
        <el-button text @click="$router.push('/')">去登录</el-button>
      </el-alert>
      <el-form label-position="top" size="large" @submit.prevent="confirmPurchase">
        <el-form-item label="收货人"><el-input v-model="purchase.buyerName" required maxlength="10" placeholder="请输入收货人姓名" :disabled="submitting || submitted" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="purchase.telephone" required maxlength="11" placeholder="11 位联系电话" :disabled="submitting || submitted" /></el-form-item>
        <el-form-item label="收货地址"><el-input v-model="purchase.address" required maxlength="99" placeholder="详细收货地址" :disabled="submitting || submitted" /></el-form-item>
        <el-button type="primary" native-type="submit" class="full-width" :loading="submitting" :disabled="!currentUser || hasUnavailable || submitted">
          {{ submitted ? '订单已提交' : fromCart ? '合并提交订单' : '提交订单' }}
        </el-button>
      </el-form>
    </section>
  </div>
  <el-empty v-else description="没有可结算的商品"><el-button @click="$router.push(fromCart ? '/cart' : '/buyer')">{{ fromCart ? '返回购物车' : '浏览商品' }}</el-button></el-empty>
</template>
<script>
import { ElMessage } from 'element-plus';
import axios from 'axios';

export default {
  data: () => ({
    products: [], currentUser: null, loading: true, submitting: false, submitted: false, error: '',
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
    validateForm() {
      if (!this.currentUser) return '请先登录后再下单';
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
.checkout-item + .checkout-item { border-top: 1px solid var(--el-border-color-lighter); margin-top: 20px; padding-top: 20px; }
.checkout-item-quantity { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-top: 16px; }
</style>
