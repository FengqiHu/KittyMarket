<template>
  <div class="section-heading">
    <div><h1>猫粮推荐</h1><p>为指定猫咪筛选适用主粮，并从当前商品信息生成检索说明。</p></div>
    <el-button :disabled="!selectedCatId || loading" @click="fetchRecommendedCatFood"><el-icon><IconRefresh /></el-icon>刷新推荐</el-button>
  </div>
  <section class="recommend-banner">
    <el-icon><IconCollection /></el-icon>
    <div><strong>选择一只猫咪</strong><p>品种、年龄和体重用于筛选商品；建议喂食量仅供参考。</p></div>
    <el-select v-model="selectedCatId" placeholder="选择猫咪" aria-label="选择猫咪" :disabled="loading || !cats.length" @change="fetchRecommendedCatFood">
      <el-option v-for="cat in cats" :key="cat.catid" :label="cat.catname" :value="cat.catid" />
    </el-select>
    <el-button @click="$router.push('/buyer-show-cat')">管理猫咪档案</el-button>
  </section>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
  <section v-if="result && result.summary" class="surface rag-summary">
    <strong>{{ result.mode === 'rag' ? 'Qwen RAG 推荐依据' : '推荐依据' }}</strong>
    <p>{{ result.summary }}</p>
    <small v-if="result.mode === 'rag' && result.sourceIds.length" class="muted">依据商品 #{{ result.sourceIds.join('、#') }} 的当前档案</small>
  </section>
  <el-skeleton v-if="loading" :rows="5" animated class="surface" />
  <div v-else class="product-grid">
    <article v-for="item in items" :key="item.goodid" class="product-card">
      <button class="product-cover" @click="showGood(item.goodid)">
        <ProductMedia :path="firstPicture(item.picture)" :alt="item.goodname" />
        <span class="product-category">适合 {{ result.catname }}</span>
      </button>
      <div class="product-card-body"><h3>{{ item.goodname }}</h3>
        <p>建议每日喂食 {{ Number(item.weight).toFixed(1) }} g</p>
        <div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span><el-button size="small" @click="showGood(item.goodid)">查看详情</el-button></div>
      </div>
    </article>
  </div>
  <el-empty v-if="!loading && !error && !cats.length" description="请先添加猫咪档案"><el-button type="primary" @click="$router.push('/buyer-show-cat')">添加猫咪</el-button></el-empty>
  <el-empty v-else-if="!loading && !error && result && !items.length" description="暂无适合这只猫咪的在售主粮" />
</template>

<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';

export default {
  data: () => ({ cats: [], selectedCatId: null, result: null, loading: false, error: '' }),
  computed: { items() { return this.result?.items || []; } },
  created() { this.loadCats(); },
  methods: {
    async loadCats() {
      this.loading = true; this.error = '';
      try {
        const { data } = await axios.get('/cat/show-cat');
        if (data.page !== 'success') throw new Error(data.msg || '猫咪档案加载失败');
        this.cats = data.data || [];
        const requested = Number(this.$route.query.catid);
        this.selectedCatId = this.cats.find(cat => cat.catid === requested)?.catid || this.cats[0]?.catid || null;
        if (this.selectedCatId) await this.fetchRecommendedCatFood();
      } catch (error) { this.error = error.message || '猫咪档案加载失败'; }
      finally { this.loading = false; }
    },
    async fetchRecommendedCatFood() {
      if (!this.selectedCatId) return;
      this.loading = true; this.error = ''; this.result = null;
      try {
        const { data } = await axios.get(`/cat/recommend/${this.selectedCatId}`);
        if (data.page !== 'success') throw new Error(data.msg || '推荐生成失败');
        this.result = data.data;
      } catch (error) { this.error = error.message || '推荐生成失败，请稍后重试'; }
      finally { this.loading = false; }
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
