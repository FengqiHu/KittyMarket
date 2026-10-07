<template>
<section class="legacy-page">
  <div v-if="isLoggedIn">
    <div id="a">
    <div class="container">
      <div class="centered-container">
        <h2>历史商品信息</h2>
      </div>
      <div class="centered-container">
      <div class="search-bar">
        <input type="text" v-model="searchQuery" class="custom-input" placeholder="搜索商品">
        <select v-model="selectedCategory"  id="search_kind">
            <option value="猫咪主粮">猫咪主粮</option>
            <option value="猫咪零食">猫咪零食</option>
            <option value="猫咪日用">猫咪日用</option>
        </select>
        <button @click="searchGoods"  class="custom-button">搜索</button>
      </div>
      </div>

      <el-table :data="paginatedGoods" empty-text="暂无记录"><el-table-column label="ID" min-width="120"><template #default="{row: good}">{{ good.goodid }}</template></el-table-column>
<el-table-column label="名称" min-width="120"><template #default="{row: good}">{{ good.goodname }}</template></el-table-column>
<el-table-column label="描述" min-width="120"><template #default="{row: good}">{{ good.description }}</template></el-table-column>
<el-table-column label="单价" min-width="120"><template #default="{row: good}">{{ good.price }}</template></el-table-column>
<el-table-column label="展示内容" min-width="160"><template #default="{row: good}">
              <div class="media-container">
                <div v-for="(media, index) in good.mediaFiles" :key="index" v-show="media.isActive">
                  <ProductMedia :path="media.url" :alt="good.goodname" />
                </div>
              </div>
              <div>
                <button @click="showPrevMedia(good)">＜</button>
                <button @click="showNextMedia(good)">＞</button>
              </div>
            </template></el-table-column>
<el-table-column label="卡路里" min-width="120"><template #default="{row: good}">{{ good.calorie }}</template></el-table-column>
<el-table-column label="适用品种" min-width="120"><template #default="{row: good}">{{ good.catkind }}</template></el-table-column>
<el-table-column label="适用体重" min-width="120"><template #default="{row: good}">{{ good.catweight }}</template></el-table-column>
<el-table-column label="适用年龄" min-width="120"><template #default="{row: good}">{{ good.catage }}</template></el-table-column>
<el-table-column label="类别" min-width="120"><template #default="{row: good}">{{ good.kind }}</template></el-table-column>
<el-table-column label="子类别" min-width="120"><template #default="{row: good}">{{ good.subkind }}</template></el-table-column>
<el-table-column label="创建日期" min-width="120"><template #default="{row: good}">{{ good.createdate }}</template></el-table-column></el-table>

      <!-- 分页 -->
      <div class="pagination">
        <button @click="goToPrevPage" class="prev" :disabled="currentPage === 1">上一页</button>
        <span id="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button @click="goToNextPage" class="next" :disabled="currentPage === totalPages">下一页</button>
      </div>
    </div>
    </div>
  </div>
  <div v-else class="else">
    您还未登录，请先<router-link to="/">登录</router-link>
  </div>
</section>
</template>
<script>
import axios from "axios";
export default {
  data() {
    return {
      products: [],
      searchQuery: '',
      selectedGood: null, // 当前选中的商品对象
      selectedCategory: '猫咪主粮', // 初始化为空字符串或其他初始值
      currentPage: 1,
      pageSize: 6, // 每页显示的商品数量
      goods: [], // 存储从服务器获取的所有商品
      filteredGoods: [], // 存储过滤后的商品
      currentUser:null,
      isHistory: 1,
    };
  },
  created() {
    this.fetchUsrFromSession();
  },
  computed: {
    isLoggedIn() {
      // 根据当前用户数据判断用户是否登录
      return !!this.currentUser;
    },
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
    console.log('ShowHistoryGoods 组件已挂载');
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
        const goodsResponse = await axios.get('/good/seller-all-historygood-list-control');
        // 解析响应数据
        // console.log('goodList:', goodsResponse);
        const goodList = goodsResponse.data.data;//goodsResponse的数据的data属性
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
            subkind: good.subkind,
            createdate: good.createdate
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
    isVideo(media) {
      const isMediaVideo = media.url.endsWith('.mp4');
      console.log(`Is media a video: ${isMediaVideo}`);
      return isMediaVideo;
    },
    async searchGoods() {
      console.log('搜索已执行');
      const credentials = {
        keyword: this.searchQuery,
        kind: this.selectedCategory,
        ishistory: 1
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
    fetchProducts() {
      // 从后端获取产品列表
    }
  },

  watch: {
    // 监视搜索结果的变化
    filteredGoods(newValue, oldValue) {
        if (newValue !== oldValue) {
            this.resetPage();
        }
    }
  },

};
</script>
