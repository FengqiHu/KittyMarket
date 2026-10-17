<template>
  <div class="section-heading">
    <div><h1>商品搜索</h1><p>按需求查找在售商品，结果来自当前商品目录。</p></div>
    <el-tag round effect="plain">{{ mode === 'rag' ? 'Qwen RAG 检索' : '商品检索' }}</el-tag>
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
  <section v-if="summary && !error" class="surface rag-summary">
    <strong>检索说明</strong><p>{{ summary }}</p>
    <small v-if="mode === 'rag'" class="muted">依据商品 #{{ sourceIds.join('、#') }} 的当前档案</small>
  </section>
  <el-skeleton v-if="loading" :rows="5" animated class="surface" />
  <div v-else class="product-grid">
    <article v-for="item in paginatedItems" :key="item.goodid" class="product-card">
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
  <el-empty v-if="!loading && !error && !items.length" description="没有匹配的在售商品，请尝试其他描述。" />
  <div v-if="items.length > pageSize" class="pagination"><el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="items.length" layout="prev, pager, next" background /></div>
</template>

<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';

export default {
  data: () => ({
    searchQuery: '', selectedCategory: '全部', items: [], summary: '', sourceIds: [], mode: 'keyword',
    loading: false, error: '', currentPage: 1, pageSize: 8
  }),
  computed: {
    paginatedItems() { return this.items.slice((this.currentPage - 1) * this.pageSize, this.currentPage * this.pageSize); }
  },
  created() { this.syncQuery(); this.load(); },
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
    async load() {
      this.loading = true; this.error = '';
      try {
        const { data } = await axios.get('/good/rag-search', { params: { query: this.searchQuery.trim(), kind: this.selectedCategory } });
        if (data.page !== 'success') throw new Error(data.msg || '商品检索失败');
        this.items = data.data.items || [];
        this.summary = data.data.summary || '';
        this.sourceIds = data.data.sourceIds || [];
        this.mode = data.data.mode;
        this.currentPage = 1;
      } catch (error) {
        this.items = []; this.summary = '';
        this.error = error.message || '商品检索失败，请稍后重试';
      } finally { this.loading = false; }
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
