<template>
<section class="catalog-list" aria-labelledby="search-title">
  <div class="section-heading">
    <div><h1 id="search-title">商品搜索</h1><p>按需求查找在售商品，结果来自当前商品目录。</p></div>
    <span class="muted">共 {{ total }} 件商品</span>
  </div>
  <form class="search-toolbar surface" @submit.prevent="submit">
    <el-input v-model="searchQuery" placeholder="例如：适合幼猫的鸡肉主粮" size="large" clearable>
      <template #prefix><el-icon><IconSearch /></el-icon></template>
    </el-input>
    <el-select v-model="selectedCategory" size="large" aria-label="商品分类">
      <el-option v-for="category in ['全部','猫咪主粮','猫咪零食','猫咪日用']" :key="category" :label="category" :value="category" />
    </el-select>
    <el-button type="primary" native-type="submit" size="large">搜索</el-button>
  </form>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
  <div class="catalog-products-region">
    <el-skeleton v-if="loading && !items.length" :rows="5" animated class="surface" />
    <el-empty v-else-if="!error && !items.length" description="没有匹配的在售商品，请尝试其他描述。" />
    <div v-else class="product-grid catalog-product-grid" :aria-busy="loading">
      <article v-for="item in items" :key="item.goodid" class="product-card">
        <button class="product-cover" @click="showGood(item.goodid)">
          <ProductMedia :path="firstPicture(item.picture)" :alt="item.goodname" />
          <span class="product-category">{{ item.subkind || item.kind }}</span>
        </button>
        <div class="product-card-body">
          <span class="product-kicker">{{ item.kind }} · #{{ item.goodid }}</span>
          <h3>{{ item.goodname }}</h3><p>{{ item.description }}</p>
          <div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span><el-button size="small" @click="showGood(item.goodid)">查看详情</el-button></div>
          <span class="stock-note">库存 {{ item.number }} 件</span>
        </div>
      </article>
    </div>
  </div>
  <div class="pagination catalog-pagination">
    <el-pagination :current-page="currentPage" :page-size="pageSize" :pager-count="5" :total="total" :disabled="loading" layout="total, prev, pager, next, jumper" background hide-on-single-page @current-change="changePage" />
  </div>
</section>
</template>

<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { preloadProductCovers } from '../utils/productList';
import '../styles/catalog-list.css';

export default {
  data: () => ({
    searchQuery: '', selectedCategory: '全部', items: [],
    loading: false, error: '', currentPage: 1, pageSize: 8, total: 0, requestId: 0
  }),
  created() { this.syncQuery(); this.load(); },
  beforeUnmount() { this.requestId++; },
  watch: { '$route.query': { handler() { this.syncQuery(); this.load(); } } },
  methods: {
    syncQuery() {
      this.searchQuery = typeof this.$route.query.query === 'string' ? this.$route.query.query : '';
      const kind = this.$route.query.kind;
      this.selectedCategory = ['全部', '猫咪主粮', '猫咪零食', '猫咪日用'].includes(kind) ? kind : '全部';
    },
    async submit() {
      const query = this.searchQuery.trim();
      if (query === (this.$route.query.query || '') && this.selectedCategory === (this.$route.query.kind || '全部')) {
        await this.load();
      } else {
        await this.$router.push({ path: '/buyer-search', query: { query, kind: this.selectedCategory } });
      }
    },
    changePage(page) {
      if (page !== this.currentPage) this.load(page);
    },
    async load(page = 1) {
      const requestId = ++this.requestId;
      this.loading = true; this.error = '';
      try {
        const { data } = await axios.get('/good/rag-search', { params: { query: this.searchQuery.trim(), kind: this.selectedCategory, page, size: this.pageSize } });
        if (requestId !== this.requestId) return;
        if (data.page !== 'success') throw new Error(data.msg || '商品检索失败');
        if (!data.data || !Array.isArray(data.data.items)) throw new Error('商品数据格式错误');
        const items = data.data.items;
        if (this.items.length) await preloadProductCovers(items.map(item => this.firstPicture(item.picture)));
        if (requestId !== this.requestId) return;
        this.items = items;
        this.total = Number(data.data.total) || 0;
        this.currentPage = page;
      } catch (error) {
        if (requestId !== this.requestId) return;
        this.error = error.message || '商品检索失败，请稍后重试';
      } finally {
        if (requestId === this.requestId) this.loading = false;
      }
    },
    firstPicture(path) { return (path || '').split(',')[0].trim(); },
    async showGood(id) {
      try {
        const { data } = await axios.post(`/good/buyer-show-good/${id}`);
        if (data.page === '/buyer-shop') this.$router.push(data.page);
        else ElMessage.error(data.msg || '商品详情加载失败');
      } catch { ElMessage.error('商品详情加载失败'); }
    }
  }
};
</script>
