<template>
  <div class="section-heading">
    <div><h1>确认订单</h1><p>核对购买数量、金额和收货信息。</p></div>
    <el-button @click="$router.push('/buyer')">返回商品列表</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
  <el-skeleton v-if="loading" :rows="5" animated class="surface" />
  <div v-else-if="selectedProduct.goodid" class="checkout-layout">
    <section class="surface">
      <ProductMedia :path="selectedProduct.picture" :alt="selectedProduct.goodname" />
      <h2>{{ selectedProduct.goodname }}</h2>
      <p class="muted">{{ selectedProduct.description }}</p>
      <el-divider />
      <p class="muted">单价 ¥{{ Number(selectedProduct.price).toFixed(2) }} · 剩余库存 {{ selectedProduct.number }} 件</p>
      <span class="product-price"><small>¥</small>{{ total }}</span>
    </section>
    <section class="surface">
      <el-alert v-if="!currentUser" title="请登录后提交订单" type="info" :closable="false" class="form-alert">
        <el-button text @click="$router.push('/')">去登录</el-button>
      </el-alert>
      <el-form label-position="top" size="large" @submit.prevent="confirmPurchase">
        <el-form-item label="购买数量"><el-input-number v-model="purchase.number" :min="1" :max="Math.max(1, selectedProduct.number)" :precision="0" :disabled="submitting || submitted" /></el-form-item>
        <el-form-item label="收货人"><el-input v-model="purchase.buyerName" required maxlength="10" placeholder="请输入收货人姓名" :disabled="submitting || submitted" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="purchase.telephone" required maxlength="11" placeholder="11 位联系电话" :disabled="submitting || submitted" /></el-form-item>
        <el-form-item label="收货地址"><el-input v-model="purchase.address" required maxlength="99" placeholder="详细收货地址" :disabled="submitting || submitted" /></el-form-item>
        <el-button type="primary" native-type="submit" class="full-width" :loading="submitting" :disabled="!currentUser || selectedProduct.state !== 0 || selectedProduct.number < 1 || submitted">
          {{ submitted ? '订单已提交' : '提交订单' }}
        </el-button>
      </el-form>
    </section>
  </div>
  <el-empty v-else description="没有可结算的商品"><el-button @click="$router.push('/buyer')">浏览商品</el-button></el-empty>
</template>
<script>
import { ElMessage } from 'element-plus';
import axios from 'axios';

export default {
  data() {
    const quantity = Number(this.$route.query.number);
    return {
      selectedProduct: {}, currentUser: null, loading: true, submitting: false, submitted: false, error: '',
      purchase: { number: Number.isInteger(quantity) && quantity > 0 ? quantity : 1, buyerName: '', telephone: '', address: '' },
    };
  },
  computed: {
    total() { return (Number(this.selectedProduct.price || 0) * (this.purchase.number || 0)).toFixed(2); },
  },
  created() { this.loadCheckout(); },
  methods: {
    async loadCheckout() {
      this.loading = true;
      try {
        const goodid = Number(this.$route.query.goodid);
        const [product, user] = await Promise.all([
          axios.get(goodid > 0 ? '/good/details/' + goodid : '/now-good'),
          axios.get('/now-usr'),
        ]);
        this.selectedProduct = product.data || {};
        this.currentUser = user.data || null;
        if (this.currentUser) {
          this.purchase.buyerName = this.currentUser.username;
          this.purchase.telephone = this.currentUser.phone || '';
          this.purchase.address = this.currentUser.address || '';
        }
        if (this.selectedProduct.goodid && (this.selectedProduct.state !== 0 || this.selectedProduct.number < 1)) {
          this.error = '商品已下架或售罄，请选择其他商品。';
        }
      } catch {
        this.error = '结算信息加载失败，请返回商品页重试。';
      } finally { this.loading = false; }
    },
    validateForm() {
      if (!this.currentUser) return '请先登录后再下单';
      if (!Number.isInteger(this.purchase.number) || this.purchase.number < 1 || this.purchase.number > this.selectedProduct.number) return '购买数量需要为正整数且不能超过剩余库存';
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
        const { data } = await axios.post('/order/createorder-control', {
          ...this.purchase, goodid: this.selectedProduct.goodid, fromCart: this.$route.query.fromCart === '1',
        });
        if (data.page === '/error' || !data.data?.orderid) { this.error = data.msg || '下单失败，请重试'; return; }
        this.submitted = true;
        ElMessage.success('订单 #' + data.data.orderid + ' 已提交');
        this.$router.push({ path: '/buyer-history', query: { orderid: data.data.orderid } });
      } catch {
        this.error = '提交失败，请检查“我的订单”确认是否已创建订单。';
      } finally { this.submitting = false; }
    },
  },
};
</script>
