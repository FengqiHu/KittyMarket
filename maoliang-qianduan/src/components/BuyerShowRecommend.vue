<template>
  <div class="section-heading">
    <div><h1>猫粮推荐</h1><p>从在售主粮中挑选，每批最多展示 12 款。</p></div>
    <el-button :disabled="!selectedCatId || loading" @click="refreshRecommendedCatFood"><el-icon><IconRefresh /></el-icon>换一批</el-button>
  </div>
  <section class="recommend-banner">
    <el-icon><IconCollection /></el-icon>
    <div><strong>选择一只猫咪</strong><p>已标注的年龄、体重范围会参与筛选；未标注的适用范围请查看商品详情确认。</p></div>
    <el-select v-model="selectedCatId" placeholder="选择猫咪" aria-label="选择猫咪" :disabled="loading || !cats.length" @change="selectCat">
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
        <span class="product-category">推荐给 {{ result.catname }}</span>
      </button>
      <div class="product-card-body"><h3>{{ item.goodname }}</h3>
        <div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span><el-button size="small" @click="showGood(item.goodid)">查看详情</el-button></div>
      </div>
    </article>
  </div>
  <el-empty v-if="!loading && !error && !cats.length" description="请先添加猫咪档案"><el-button type="primary" @click="$router.push('/buyer-show-cat')">添加猫咪</el-button></el-empty>
  <el-empty v-else-if="!loading && !error && result && !items.length" description="暂无可推荐的在售主粮" />
</template>

<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';

const CACHE_TTL_MS = 30 * 60 * 1000;
const CACHE_PREFIX = 'kittymarket:cat-recommend:v2:';

export default {
  data: () => ({ cats: [], userId: null, selectedCatId: null, batch: 0, result: null, loading: false, error: '' }),
  computed: { items() { return this.result?.items || []; } },
  created() { this.loadCats(); },
  methods: {
    async loadCats() {
      this.loading = true; this.error = '';
      try {
        const [{ data }, { data: user }] = await Promise.all([
          axios.get('/cat/show-cat'), axios.get('/now-usr')
        ]);
        if (data.page !== 'success') throw new Error(data.msg || '猫咪档案加载失败');
        if (!user?.userid) throw new Error('请先登录后查看猫粮推荐');
        this.userId = user.userid;
        this.cats = data.data || [];
        const requested = Number(this.$route.query.catid);
        let previous;
        try { previous = Number(sessionStorage.getItem(`${CACHE_PREFIX}${this.userId}:selected`)); } catch { /* Storage is optional. */ }
        this.selectedCatId = this.cats.find(cat => cat.catid === requested)?.catid ||
          this.cats.find(cat => cat.catid === previous)?.catid || this.cats[0]?.catid || null;
        if (this.selectedCatId) await this.showRecommendedCatFood();
      } catch (error) { this.error = error.message || '猫咪档案加载失败'; }
      finally { this.loading = false; }
    },
    cacheKey() { return `${CACHE_PREFIX}${this.userId}:${this.selectedCatId}`; },
    catFingerprint() {
      const cat = this.cats.find(item => item.catid === this.selectedCatId);
      return cat ? JSON.stringify([cat.catname, cat.catkind, cat.catage, cat.catweight, cat.catstate, cat.description]) : '';
    },
    restoreCache() {
      try {
        const cached = JSON.parse(sessionStorage.getItem(this.cacheKey()) || 'null');
        if (!cached || cached.fingerprint !== this.catFingerprint() ||
            !Number.isSafeInteger(cached.batch) || cached.batch < 0 ||
            !Number.isFinite(cached.savedAt) || cached.savedAt > Date.now() ||
            Date.now() - cached.savedAt > CACHE_TTL_MS || !Array.isArray(cached.result?.items)) return false;
        this.batch = cached.batch;
        this.result = cached.result;
        return true;
      } catch { return false; }
    },
    saveCache() {
      try {
        sessionStorage.setItem(this.cacheKey(), JSON.stringify({
          batch: this.batch, result: this.result, fingerprint: this.catFingerprint(), savedAt: Date.now()
        }));
      } catch { /* Browser storage may be unavailable; recommendations still work. */ }
    },
    showRecommendedCatFood() {
      this.error = '';
      try { sessionStorage.setItem(`${CACHE_PREFIX}${this.userId}:selected`, this.selectedCatId); } catch { /* Storage is optional. */ }
      if (this.restoreCache()) return;
      this.batch = 0;
      this.result = null;
      return this.fetchRecommendedCatFood();
    },
    selectCat() { this.showRecommendedCatFood(); },
    refreshRecommendedCatFood() { return this.fetchRecommendedCatFood(this.batch + 1); },
    async fetchRecommendedCatFood(batch = this.batch) {
      if (!this.selectedCatId || this.loading && this.result) return;
      this.loading = true; this.error = '';
      try {
        const { data } = await axios.get(`/cat/recommend/${this.selectedCatId}`, { params: { batch } });
        if (data.page !== 'success') throw new Error(data.msg || '推荐生成失败');
        if (!Array.isArray(data.data?.items)) throw new Error('推荐数据格式错误');
        this.result = data.data;
        this.batch = batch;
        this.saveCache();
      } catch (error) {
        this.error = error.message || '推荐生成失败，请稍后重试';
      }
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
