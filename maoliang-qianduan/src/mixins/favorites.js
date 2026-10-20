import axios from 'axios';
import { ElMessage } from 'element-plus';

export default {
  data: () => ({
    favoriteIds: [],
    favoritesLoading: true,
    favoritesError: false,
    pendingFavoriteIds: [],
    favoritesRequest: 0,
  }),
  methods: {
    isFavorite(goodId) { return this.favoriteIds.includes(Number(goodId)); },
    favoriteBusy(goodId) {
      return this.favoritesLoading || this.pendingFavoriteIds.includes(Number(goodId));
    },
    favoriteLabel(goodId) {
      if (this.favoritesError) return '重试加载收藏';
      return this.isFavorite(goodId) ? '已收藏' : '收藏';
    },
    async loadFavorites(userId) {
      const request = ++this.favoritesRequest;
      this.favoriteIds = [];
      this.favoritesError = false;
      this.favoritesLoading = true;
      try {
        if (!userId) return;
        const { data } = await axios.post('/cart/showLike', null, { params: { userId } });
        if (!Array.isArray(data)) throw new Error('收藏数据格式错误');
        if (request === this.favoritesRequest) {
          this.favoriteIds = data.map(row => Number(row.goodid ?? row.GOODID));
        }
      } catch {
        if (request === this.favoritesRequest) {
          this.favoritesError = true;
          ElMessage.error('收藏状态加载失败，请点击重试');
        }
      } finally {
        if (request === this.favoritesRequest) this.favoritesLoading = false;
      }
    },
    async toggleFavorite(goodId, userId) {
      goodId = Number(goodId);
      if (!userId) { ElMessage.error('请先登录后再收藏商品'); return; }
      if (this.favoriteBusy(goodId)) return;
      if (this.favoritesError) { await this.loadFavorites(userId); return; }
      const wasFavorite = this.isFavorite(goodId);
      this.pendingFavoriteIds.push(goodId);
      try {
        const { data } = await axios.post('/cart/addLike', null, {
          params: { goodid: goodId, iscancel: wasFavorite ? 1 : 0, userid: userId },
        });
        // This legacy endpoint returns a page string even when the session has expired.
        if (data !== '/success') throw new Error('收藏操作失败，请确认登录状态后重试');
        this.favoriteIds = wasFavorite
          ? this.favoriteIds.filter(id => id !== goodId)
          : [...this.favoriteIds, goodId];
        ElMessage.success(wasFavorite ? '已取消收藏' : '已加入收藏');
      } catch (error) {
        ElMessage.error(error.message || '收藏操作失败，请重试');
      } finally {
        this.pendingFavoriteIds = this.pendingFavoriteIds.filter(id => id !== goodId);
      }
    },
  },
};
