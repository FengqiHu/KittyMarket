<template>
  <section class="catalog-list" aria-labelledby="hot-goods-title">
    <div class="section-heading">
      <div>
        <h1 id="hot-goods-title">热门商品</h1>
        <p>{{ isBuyer ? '结合你的浏览、购买偏好与猫咪档案，为你推荐好物' : '发现人气好物，登录后获取专属推荐' }}</p>
      </div>
      <el-button @click="$router.push('/buyer-show-recommend')">根据猫咪档案选粮<el-icon class="icon-after"><IconArrowRight /></el-icon></el-button>
    </div>
    <form class="search-toolbar surface" @submit.prevent="handleSearch">
      <el-input v-model="searchQuery" placeholder="描述想找的商品，例如适合幼猫的鸡肉主粮" size="large" clearable>
        <template #prefix><el-icon><IconSearch /></el-icon></template>
      </el-input>
      <el-select v-model="selectedCategory" size="large" aria-label="商品分类">
        <el-option v-for="category in categories" :key="category" :label="category" :value="category" />
      </el-select>
      <el-button type="primary" native-type="submit" size="large">搜索</el-button>
    </form>
    <div class="category-toolbar">
      <div class="category-tabs" aria-label="商品分类">
        <button v-for="category in categories" :key="category" :class="{ 'is-active': categoryView === category }" :aria-pressed="categoryView === category" @click="filterCategory(category)">{{ category }}</button>
      </div>
      <span class="muted">{{ isBuyer ? '为你推荐' : '人气排序' }} · 共 {{ totalItems }} 件商品</span>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="form-alert">
      <el-button text @click="fetchGoods()">重新加载</el-button>
    </el-alert>
    <div class="catalog-products-region">
    <el-skeleton v-if="loading && !pageItems.length" :rows="5" animated class="surface" />
    <el-empty v-else-if="!loadError && !totalItems" description="暂无该分类商品，试试其他分类。" />
    <div v-else class="product-grid catalog-product-grid" :aria-busy="loading">
      <article v-for="item in pageItems" :key="item.goodid" class="product-card">
        <button class="product-cover" @click="openProduct(item.goodid)">
          <ProductMedia :path="item.mediaFiles[item.activeMedia]" :alt="item.goodname" />
          <span class="product-category">{{ item.subkind || item.kind }}</span>
        </button>
        <div v-if="item.mediaFiles.length > 1" class="media-controls">
          <el-button circle size="small" aria-label="上一张" @click="changeMedia(item, -1)"><el-icon><IconArrowLeft /></el-icon></el-button>
          <el-button circle size="small" aria-label="下一张" @click="changeMedia(item, 1)"><el-icon><IconArrowRight /></el-icon></el-button>
        </div>
        <div class="product-card-body">
          <span class="product-kicker">{{ item.kind }}</span><h3>{{ item.goodname }}</h3><p>{{ item.description }}</p>
          <div class="product-card-bottom">
            <span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span>
            <el-button size="small" @click="openProduct(item.goodid)">查看详情</el-button>
          </div>
          <span class="stock-note">{{ item.number > 0 ? '库存 ' + item.number + ' 件' : '暂时售罄' }}</span>
        </div>
      </article>
    </div>
    </div>
    <div class="pagination catalog-pagination">
      <el-pagination :current-page="currentPage" :page-size="itemsPerPage" :pager-count="5" :total="totalItems" :disabled="loading" layout="total, prev, pager, next, jumper" background hide-on-single-page @current-change="changePage" />
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
    pageItems: [], totalItems: 0, loading: true, loadError: '', currentUser: null,
    searchQuery: '', categoryView: '全部', selectedCategory: '全部',
    categories: ['全部', '猫咪主粮', '猫咪零食', '猫咪日用'],
    currentPage: 1, itemsPerPage: 8, pageRequestId: 0
  }),
  computed: {
    isBuyer() { return this.currentUser && this.currentUser.power === 0; }
  },
  mounted() {
    this.fetchUser();
    this.fetchGoods();
  },
  beforeUnmount() { this.pageRequestId++; },
  methods: {
    async fetchUser() {
      try {
        const { data } = await axios.get('/now-usr');
        this.currentUser = data || null;
      } catch { this.currentUser = null; }
    },
    filterCategory(category) {
      this.categoryView = category;
      this.currentPage = 1;
      this.fetchGoods();
    },
    changePage(page) {
      if (page === this.currentPage) return;
      this.fetchGoods(page);
    },
    async fetchGoods(page = this.currentPage) {
      const requestId = ++this.pageRequestId;
      this.loading = true;
      this.loadError = '';
      try {
        const { data } = await axios.get('/good/buyer-goods-page', {
          params: { category: this.categoryView, page, size: this.itemsPerPage }
        });
        if (requestId !== this.pageRequestId) return;
        if (!data.data || !Array.isArray(data.data.items)) throw new Error('商品数据格式错误');
        const items = data.data.items.map(good => ({
          ...good,
          goodname: (good.goodname || '').trim(),
          description: (good.description || '').trim(),
          mediaFiles: (good.picture || '').split(',').map(path => path.trim()).filter(Boolean),
          activeMedia: 0
        }));
        if (this.pageItems.length) await preloadProductCovers(items.map(item => item.mediaFiles[0]));
        if (requestId !== this.pageRequestId) return;
        this.totalItems = data.data.total;
        this.pageItems = items;
        this.currentPage = page;
      } catch (error) {
        if (requestId !== this.pageRequestId) return;
        this.loadError = '商品加载失败，请稍后重试';
      } finally {
        if (requestId === this.pageRequestId) this.loading = false;
      }
    },
    async openProduct(goodid) {
      try {
        const { data } = await axios.post(`/good/buyer-show-good/${goodid}`);
        if (data.page === '/error') {
          this.$router.push({ path: '/error', query: { err: data.msg, to: data.data } });
        } else if (data.page) {
          this.$router.push({ path: data.page });
        }
      } catch { ElMessage.error('商品打开失败，请重试'); }
    },
    handleSearch() {
      this.$router.push({ path: '/buyer-search', query: { query: this.searchQuery.trim(), kind: this.selectedCategory } });
    },
    changeMedia(item, direction) {
      item.activeMedia = (item.activeMedia + direction + item.mediaFiles.length) % item.mediaFiles.length;
    }
  }
};
</script>
