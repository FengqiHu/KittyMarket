<template>
<div class="section-heading"><div><h1>商品搜索结果</h1><p>查看商品信息，调整价格和销售状态。</p></div><el-button type="primary" size="large" @click="$router.push('/seller/upload-onegood')"><el-icon><IconPlus /></el-icon>发布商品</el-button></div>
<div class="stats-grid"><div class="stat-card"><div><span>商品总数</span><strong>{{ goods.length }}</strong><small>当前店铺商品</small></div></div><div class="stat-card"><div><span>在售商品</span><strong>{{ goods.filter(good => good.state === 0).length }}</strong><small>当前上架商品</small></div></div><div class="stat-card"><div><span>库存待补充</span><strong>{{ goods.filter(good => good.number < 5 && good.state === 0).length }}</strong><small>库存少于 5 件</small></div></div></div>
<section class="surface table-surface"><form class="search-toolbar" @submit.prevent="searchGoods"><el-input v-model="searchQuery" clearable placeholder="搜索商品名称"><template #prefix><el-icon><IconSearch /></el-icon></template></el-input><el-select v-model="selectedCategory" aria-label="商品分类"><el-option v-for="category in ['猫咪主粮','猫咪零食','猫咪日用']" :key="category" :value="category" :label="category" /></el-select><el-button native-type="submit" type="primary">搜索</el-button></form>
<el-table :data="paginatedGoods" empty-text="暂无商品" style="width:100%"><el-table-column label="商品" min-width="260"><template #default="{row}"><div class="table-product"><ProductMedia :path="row.picture" :alt="row.goodname" /><div><strong>{{ row.goodname }}</strong><span>{{ row.description }}</span><small>#{{ row.goodid }} · {{ row.subkind }}</small></div></div></template></el-table-column><el-table-column label="价格" width="115"><template #default="{row}"><strong>¥{{ Number(row.price).toFixed(2) }}</strong></template></el-table-column><el-table-column label="库存" width="100"><template #default="{row}"><el-tag :type="row.number < 5 ? 'warning' : 'info'" effect="light">{{ row.number }} 件</el-tag></template></el-table-column><el-table-column label="适用猫咪" min-width="180"><template #default="{row}"><span>{{ row.catkind }}</span><div class="muted">{{ row.catage }} 岁 · {{ row.catweight }} kg</div><small class="muted">{{ row.calorie }} cal/g</small></template></el-table-column><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.state === 0 ? 'success' : 'info'" round>{{ stateText(row.state) }}</el-tag></template></el-table-column><el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openPriceModal(row)">改价</el-button><el-button link type="danger" :disabled="row.state !== 0" @click="confirmDelete(row.goodid)">下架</el-button></template></el-table-column></el-table>
<div class="table-pagination"><span class="muted">共 {{ filteredGoods.length }} 件商品</span><el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="filteredGoods.length" layout="prev, pager, next" background /></div></section><PriceModal :good="selectedGood" :isVisible="showPriceModal" @close="showPriceModal = false" @update="handleUpdatePrice" />
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus';
import PriceModal from './PriceModal.vue';
import axios from "axios";

export default {
    components: {
        PriceModal
    },
    data() {
    return {
      searchQuery: '', // 默认选中的分类
      showPriceModal: false, // 控制 PriceModal 的显示
      selectedGood: null, // 当前选中的商品对象
      showModal: false,  // 初始化为 false 或根据需要设置初始值
      selectedCategory: '猫咪主粮', // 初始化为空字符串或其他初始值
      currentPage: 1,
      pageSize: 6, // 每页显示的商品数量
      filteredGoods: [],
      isHistory: 0, // 这个值可以根据需要在data中定义，或者在方法中直接使用
      showSuccessMessage: false,
      successMessage: '',
      goods: [],
      currentUser:null,
    };
  },
  created() {
    this.fetchUsrFromSession();
    this.fetchgoodListSession();
  },
  computed: {
    isLoggedIn() {
      // 根据当前用户数据判断用户是否登录
      return !!this.currentUser;
    },
    // 计算当前页的商品
    paginatedGoods() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      return this.filteredGoods.slice(start, end);
    },
    // 计算总页数
    totalPages() {
      return Math.max(1, Math.ceil(this.filteredGoods.length / this.pageSize));
    }
  },
  async mounted() {
    console.log('ShowGoods 组件已挂载');
    await this.fetchgoodListSession(); // 等待 fetchgoodListSession 完成!!
    // 示例：假设从后端获取成功消息
    // 在实际应用中，您可能需要在某个操作成功后调用 showSuccessModal
    this.filteredGoods = this.goods; // 初始时显示所有商品
    console.log('this.filteredGoods:', this.filteredGoods);

  },

  methods: {
    async fetchUsrFromSession() {
      try {
        // 发起 GET 请求到后端接口
        const response = await axios.get('/now-usr');
        // 解析响应数据
        const usr = response.data;
        // 更新组件的 currentUser 数据
        this.currentUser = usr;

        return true;
      } catch (error) {
        console.error('获取用户数据错误:', error);
        return false;
      }
    },
    async fetchgoodListSession() {
      try {
        // 发起 GET 请求获取商品列表
        const goodsResponse = await axios.get('/searchList');
        // 解析响应数据
       // console.log('goodList:', goodsResponse);
        const goodList = goodsResponse.data;//goodsResponse的数据的data属性
        // 将商品列表添加到 products 中
        // 解析picture属性并添加mediaFiles属性
      //  console.log('goodList:', goodList);

        this.goods = goodList.map( good => {
      //    console.log('Before trimming:', good.picture); // 添加调试语句
          const trimmedPicture = good.picture.trim();
     //     console.log('After trimming:', trimmedPicture); // 添加调试语句
          const paths = trimmedPicture.split(',');
          const mediaFiles = paths.map((path, i) => {
            return {
              url: path,
              isActive: i === 0 // 默认第一个是true，其他是false
            };
          });
          return {
            ...good,
            mediaFiles,
            // 保留原始属性
            goodname: good.goodname.trim(),
            description: good.description.trim(),
            price: good.price,
            number: good.number,
            kind: good.kind,
            subkind: good.subkind
          };
        });
        console.log('this.goods:', this.goods);
        return true;
      } catch (error) {
        console.error('获取商品列表数据错误:', error);
        return false;
      }
    },
    showPrevMedia(good) {
        const currentIndex = good.mediaFiles.findIndex(media => media.isActive);
        console.log('Current active index:', currentIndex);

        if (currentIndex >= 0) {
        const prevIndex = (currentIndex - 1 + good.mediaFiles.length) % good.mediaFiles.length;
        console.log('Previous index:', prevIndex);
        this.setActiveMedia(good, prevIndex);
        }
    },
    showNextMedia(good) {
        const currentIndex = good.mediaFiles.findIndex(media => media.isActive);
        console.log('Current active index:', currentIndex);

        if (currentIndex >= 0) {
        const nextIndex = (currentIndex + 1) % good.mediaFiles.length;
        console.log('Next index:', nextIndex);
        this.setActiveMedia(good, nextIndex);
        }
    },
    setActiveMedia(good, index) {
        good.mediaFiles.forEach((media, idx) => {
        media.isActive = idx === index;
        console.log(`Media at index ${idx} active status: `, media.isActive);
        });
    },
    isVideo(media) {
        const isMediaVideo = media.url.endsWith('.mp4');
        console.log(`Is media a video: ${isMediaVideo}`);
        return isMediaVideo;
    },
    showSuccessModal(message) {
      this.successMessage = message;
      this.showSuccessMessage = true;
    },
    async searchGoods() {
      console.log('搜索已执行');
      const credentials = {
        keyword: this.searchQuery,
        kind: this.selectedCategory,
        ishistory: 0
      };

      const response = await fetch('/good/search-list-control', {
        method: 'POST',
        body: JSON.stringify(credentials),
        headers: {
          'Content-Type': 'application/json'
        }
      });

      const responseData = await response.json();

      if (responseData.page === '/error') {
        // 重定向到错误页面，并将错误消息和重定向目标作为参数传递
        this.$router.push({ path: '/error', query: { err: responseData.msg, to: responseData.data }})
      } else if (responseData.page === '/success') {
        // 重定向到成功界面，并将成功消息和重定向目标作为参数传递
        this.$router.push({ path: '/success', query: { message: responseData.msg, to: responseData.data }})
      } else if (responseData.page === null) {
        console.log("未知页面类型");
      } else {
        this.$router.push({ path: responseData.page });
      }
    },
    // 调用此方法来关闭成功消息弹窗
    closeSuccessModal() {
      this.showSuccessMessage = false;
    },
    async confirmDelete(goodId) {
      try { await ElMessageBox.confirm('确认下架该商品？', '下架商品', { confirmButtonText: '确认下架', cancelButtonText: '取消', type: 'warning' }); } catch { return; }
      this.deleteGood(goodId);
    },
    // TODO: 实现删除商品的逻辑
    deleteGood(goodId) {
        console.log('删除商品，商品ID:', goodId);
        // 模拟API调用，直接从列表中移除商品
        // 在实际应用中，这里会是一个调用后端API的异步操作
        this.filteredGoods = this.filteredGoods.filter(good => good.goodid !== goodId);
        // 显示删除成功的消息，这里可以替换为更复杂的通知系统
        ElMessage.info('商品删除成功');
        // 调用后端API删除商品
        // axios.delete('/api/goods/' + goodId)
        // .then(() => {
        // // 删除成功后从列表中移除该商品
        // this.filteredGoods = this.filteredGoods.filter(good => good.goodid !== goodId);
        // // 可以显示一个成功的提示信息
        // this.showSuccessModal('商品删除成功');
        // })
        // .catch(error => {
        // // 错误处理，显示错误信息
        // console.error('删除失败', error);
        // });
    },
    // TODO: 实现打开价格修改模态窗口的逻辑
    openPriceModal(good) {
        console.log("打开价格修改窗口!");
      this.selectedGood = good;
      this.showPriceModal = true;
    },
    handleUpdatePrice({ goodId, newPrice }) {
      // 在这里处理价格更新逻辑
      // 例如，更新商品数组中对应商品的价格
      const good = this.goods.find(g => g.goodid === goodId);
      if (good) {
        good.price = newPrice;
      }
      this.showPriceModal = false;
    },
    goToPrevPage() {
      // 实现翻页逻辑
      if (this.currentPage > 1) {
        this.currentPage--;
      }
    },
    goToNextPage() {
      // 实现翻页逻辑
        if (this.currentPage < this.totalPages) {
            this.currentPage++;
        }
    },
    // 每次搜索结果变化后重置当前页码
    resetPage() {
        this.currentPage = 1;
    },
    stateText(value) {
        const stateMap = { 0: '上架', 1: '冻结', 2: '售出' };
        return stateMap[value] || '未知';
    },
  },

  watch: {
    // 监视搜索结果的变化
    filteredGoods(newValue, oldValue) {
        if (newValue !== oldValue) {
            this.resetPage();
        }
    }
  }
};
</script>
