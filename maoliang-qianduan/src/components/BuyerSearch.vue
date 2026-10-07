<template>
<div class="section-heading"><div><h1>搜索结果</h1></div><el-tag round effect="plain">{{ filteredItems.length }} 件商品</el-tag></div>
<form class="search-toolbar surface" @submit.prevent="handleSearch"><el-input v-model="searchQuery" placeholder="输入商品名称" size="large" clearable><template #prefix><el-icon><IconSearch /></el-icon></template></el-input><el-select v-model="selectedCategory" size="large" aria-label="商品分类"><el-option v-for="category in ['猫咪主粮','猫咪零食','猫咪日用']" :key="category" :label="category" :value="category" /></el-select><el-button type="primary" native-type="submit" size="large">搜索</el-button></form>
<el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="form-alert"><el-button text @click="fetchgoodListSession">重新加载</el-button></el-alert><el-skeleton v-if="loading" :rows="5" animated class="surface" />
<div v-else class="product-grid"><article v-for="item in paginatedItems" :key="item.goodid" class="product-card"><button class="product-cover" @click="postToBuyerShop(item.goodid)"><ProductMedia :path="(item.mediaFiles.find(media => media.isActive) || {}).url" :alt="item.goodname" /><span class="product-category">{{ item.subkind || item.kind }}</span></button><div v-if="item.mediaFiles.length > 1" class="media-controls"><el-button circle size="small" aria-label="上一张" @click="showPrevMedia(item)"><el-icon><IconArrowLeft /></el-icon></el-button><el-button circle size="small" aria-label="下一张" @click="showNextMedia(item)"><el-icon><IconArrowRight /></el-icon></el-button></div><div class="product-card-body"><span class="product-kicker">{{ item.kind }}</span><h3>{{ item.goodname }}</h3><p>{{ item.description }}</p><div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span><el-button size="small"  @click="postToBuyerShop(item.goodid)">查看详情</el-button></div><span class="stock-note">{{ item.number > 0 ? '库存 ' + item.number + ' 件' : '暂时售罄' }}</span></div></article></div>
<el-empty v-if="!loading && !loadError && !filteredItems.length" description="没有匹配的商品，请尝试其他关键词。" /><div class="pagination"><el-pagination v-model:current-page="currentPage" :page-size="itemsPerPage" :total="filteredItems.length" layout="prev, pager, next" background hide-on-single-page /></div>
</template>

<script>
import axios from "axios";

export default {
  data() {
    return {
      items: [ ],
      loading: true, loadError: '',
     searchQuery: '',
     selectedCategory: '猫咪主粮', // 设置初始值为“猫咪主粮”
     filteredItems: [], // 添加这个新数组
      currentPage: 1,
      itemsPerPage: 8,
      search: {
        keyword: '',
        kind: '猫咪主粮'
      },
      currentUser :null
    };
  },

  created() {
    this.fetchUsrFromSession();
  },
  async mounted() {
    console.log('BuyerMain 组件已挂载');
    await this.fetchgoodListSession(); // 等待 fetchgoodListSession 完成!!
    this.filteredItems = this.items; // 初始时显示所有商品
  },
  computed: {
    isLoggedIn() {
      // 根据当前用户数据判断用户是否登录
      return !!this.currentUser;
    },
    isSeller() {
      // 根据当前用户数据判断用户是否是卖家
      return this.currentUser && this.currentUser.power === 1;
    },
    // 判断用户是否是买家的方法
    isBuyer() {
      // 根据当前用户数据判断用户是否是买家
      return this.currentUser && this.currentUser.power === 0;
    },
    getUsername() {
      // 如果当前用户数据不为空，则返回用户名；否则返回未登录
      return this.currentUser ? this.currentUser.username : '未登录';
    },
    paginatedItems() {
      // 计算当前页的商品
      const start = (this.currentPage - 1) * this.itemsPerPage;
      const end = this.currentPage * this.itemsPerPage;
      return this.filteredItems.slice(start, end);
    },
    // 计算总页数
    totalPages() {
      return Math.max(1, Math.ceil(this.filteredItems.length / this.itemsPerPage));
    }
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
      this.loading = true; this.loadError = '';
      try {
        // 发起 GET 请求获取商品列表
        const goodsResponse = await axios.get('/searchList');
        // 解析响应数据
        // console.log('goodList:', goodsResponse);
        const goodList = goodsResponse.data;//goodsResponse的数据的data属性
        // 将商品列表添加到 products 中
        // 解析picture属性并添加mediaFiles属性
       //  console.log('goodList:', goodList);

        this.items = goodList.map( good => {
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
            goodid: good.goodid,
            goodname: good.goodname.trim(),
            description: good.description.trim(),
            price: good.price,
            number: good.number,
            kind: good.kind,
            subkind: good.subkind,
            buyingid: good.buyingid,
            numbermax: good.numbermax,
            islike: good.islike
          };
        });
        this.filteredItems = this.items;
        return true;
      } catch (error) {
        console.error('获取商品列表数据错误:', error);
        this.loadError = '商品加载失败，请稍后重试'; return false;
      } finally { this.loading = false; }
    },

    isVideo(media) {
      //判断是否是视频
      return media.url.endsWith('.mp4') || media.url.endsWith('.avi');
    },
    async postToBuyerShop(goodid) {
      const response = await fetch(`/good/buyer-show-good/${goodid}`, {
        method: 'POST',
      });

      this.selectedFiles = []; // 清空照片列表

      // 解析响应数据
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
    async handleLogout() {
      try {
        // 向后端发送登出请求
        await axios.get('/logout-control');
        // 跳转到指定路由
        this.$router.push('/');
      } catch (error) {
        console.error('登出失败:', error);
        // 可选：处理登出失败的情况
      }
    },
    navigateTo(routeName) {
      this.$router.push({ name: routeName });
    },
    beforeMount() {
      if (!this.isLoggedIn) {
        this.$router.push('/');
      }
    },
    async handleSearch() {
      if (!this.currentUser) { this.filteredItems = this.items.filter(item => item.kind === this.selectedCategory && item.goodname.includes(this.searchQuery.trim())); this.currentPage = 1; return; }
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
    showPrevMedia(item) {
        const currentIndex = item.mediaFiles.findIndex(media => media.isActive);
        console.log('Current active index:', currentIndex);

        if (currentIndex >= 0) {
        const prevIndex = (currentIndex - 1 + item.mediaFiles.length) % item.mediaFiles.length;
        console.log('Previous index:', prevIndex);
        this.setActiveMedia(item, prevIndex);
        }
    },
    showNextMedia(item) {
        const currentIndex = item.mediaFiles.findIndex(media => media.isActive);
        console.log('Current active index:', currentIndex);

        if (currentIndex >= 0) {
        const nextIndex = (currentIndex + 1) % item.mediaFiles.length;
        console.log('Next index:', nextIndex);
        this.setActiveMedia(item, nextIndex);
        }
    },
    setActiveMedia(item, index) {
        item.mediaFiles.forEach((media, idx) => {
        media.isActive = idx === index;
        console.log(`Media at index ${idx} active status: `, media.isActive);
        });
    },
    goToPrevPage() {
      // 实现翻页逻辑
      if (this.currentPage > 1) {
        this.currentPage--;
        console.log("Current page after prev:", this.currentPage);
      }
    },
    goToNextPage() {
      // 实现翻页逻辑
        if (this.currentPage < this.totalPages) {
            this.currentPage++;
            console.log("Current page after next:", this.currentPage);
        }
    },
    // 每次搜索结果变化后重置当前页码
    resetPage() {
        this.currentPage = 1;
    },
  },

  watch: {
    // 监视搜索结果的变化
    filteredItems(newValue, oldValue) {
        if (newValue !== oldValue) {
            this.resetPage();
        }
    }
  },

};
</script>
