<template>
<div class="section-heading"><div><h1>商品详情</h1></div><el-button @click="$router.push('/buyer')"><el-icon><IconArrowLeft /></el-icon>返回商品列表</el-button></div>
<section v-if="selectedProduct.goodid" class="surface product-detail"><div><ProductMedia v-for="(media,index) in selectedProduct.mediaFiles" v-show="media.isActive" :key="index" :path="media.url" :alt="selectedProduct.goodname" /><div v-if="selectedProduct.mediaFiles.length > 1" class="media-controls"><el-button circle aria-label="上一张" @click="showPrevMedia(selectedProduct)"><el-icon><IconArrowLeft /></el-icon></el-button><el-button circle aria-label="下一张" @click="showNextMedia(selectedProduct)"><el-icon><IconArrowRight /></el-icon></el-button></div></div><div class="product-detail-copy"><el-tag round effect="plain">{{ selectedProduct.kind }} / {{ selectedProduct.subkind }}</el-tag><h1>{{ selectedProduct.goodname }}</h1><p>{{ selectedProduct.description }}</p><div class="detail-price">¥{{ Number(selectedProduct.price).toFixed(2) }}<small>/ 件</small></div><el-descriptions :column="1"><el-descriptions-item label="剩余库存">{{ selectedProduct.number }} 件</el-descriptions-item><el-descriptions-item label="适用品种">{{ selectedProduct.catkind }}</el-descriptions-item><el-descriptions-item label="适用体重">{{ selectedProduct.catweight }} kg</el-descriptions-item><el-descriptions-item label="适用年龄">{{ selectedProduct.catage }} 岁</el-descriptions-item><el-descriptions-item label="能量">{{ selectedProduct.calorie }} cal/g</el-descriptions-item></el-descriptions><div v-if="isLoggedIn" class="detail-actions"><el-button type="primary" size="large" :disabled="selectedProduct.number < 1" @click="buyProduct(selectedProduct.goodid)">立即购买</el-button><el-button size="large" :disabled="selectedProduct.number < 1" @click="addToCart"><el-icon><IconShoppingCart /></el-icon>加入购物车</el-button><el-button size="large" @click="addToFavorites"><el-icon><IconStar /></el-icon>收藏</el-button></div><el-alert v-else type="info" title="登录后可以加入购物车或收藏商品" :closable="false" show-icon><el-button text @click="$router.push('/')">去登录</el-button></el-alert></div></section><el-empty v-else description="还没有选择商品"><el-button @click="$router.push('/buyer')">浏览商品</el-button></el-empty>
</template>

<script>
import { ElMessage } from 'element-plus';
import { mediaUrl } from '../utils/media';
import { mapActions } from 'vuex';
import axios from "axios";

export default {
  data() {
    return {
      items: [],
     searchQuery: '',
      selectedProduct: [],
      currentUser:'',
     selectedCategory: '猫咪主粮', // 设置初始值为“猫咪主粮”
     filteredItems: [], // 添加这个新数组
      currentPage: 1,
      itemsPerPage: 2,
      search: {
        keyword: '',
        kind: '猫咪主粮'
      }
    };
  },
  created() {
    this.fetchUsrFromSession();
    this.fetchGoodFromSession();
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
    ...mapActions(['logout']),
    getImageUrl(picturePath) { return mediaUrl(picturePath); },
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
    async fetchGoodFromSession() {
      try {
        // 发起 GET 请求到后端接口
        const response = await axios.get('/now-good');

        // 解析响应数据
        const good = response.data;

        const trimmedPicture = good.picture.trim();
        const paths = trimmedPicture.split(',');
        const mediaFiles = paths.map((path, i) => {
          return {
            url: path,
            isActive: i === 0 // 默认第一个是true，其他是false
          };
        });

        this.selectedProduct = {
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
        console.log('this.selectedProduct:', this.selectedProduct);
        return true;
      } catch (error) {
        console.error('获取用户数据错误:', error);
        return false;
      }
    },
    handleLogout() {
      this.logout();
      this.$router.push('/');
    },
    navigateTo(routeName) {
      this.$router.push({ name: routeName });
    },
    beforeMount() {
      if (!this.isLoggedIn) {
        this.$router.push('/');
      }
    },
    showPrevMedia(product) {
        const currentIndex = product.mediaFiles.findIndex(media => media.isActive);
        console.log('Current active index:', currentIndex);

        if (currentIndex >= 0) {
        const prevIndex = (currentIndex - 1 + product.mediaFiles.length) % product.mediaFiles.length;
        console.log('Previous index:', prevIndex);
        this.setActiveMedia(product, prevIndex);
        }
    },
    showNextMedia(product) {
        const currentIndex = product.mediaFiles.findIndex(media => media.isActive);
        console.log('Current active index:', currentIndex);

        if (currentIndex >= 0) {
        const nextIndex = (currentIndex + 1) % product.mediaFiles.length;
        console.log('Next index:', nextIndex);
        this.setActiveMedia(product, nextIndex);
        }
    },
    setActiveMedia(item, index) {
        item.mediaFiles.forEach((media, idx) => {
        media.isActive = idx === index;
        console.log(`Media at index ${idx} active status: `, media.isActive);
        });
    },
    buyProduct(productId) {
      // 处理购买逻辑
      this.$router.push({ name: 'BuyerFillInfo', query: { goodid: productId } });
    },
    addToCart() {
      // 调用接口，获取详细信息
      console.log("goodid:"+this.selectedProduct.goodid);
      axios.post('/cart/create-cart', null, { params: { goodid: this.selectedProduct.goodid,userid:this.currentUser.userid } }).then(res => {
        // 处理返回的详细信息
        console.log(res.data);
        ElMessage.info("商品已添加购物车");
      }).catch(error => {
        // 处理请求错误
        console.error(error);
      });
    },
    addToFavorites() {
      // 调用接口，获取详细信息
      axios.post('/cart/addLike', null, { params: { goodid: this.selectedProduct.goodid, iscancel: 0, userid: this.currentUser.userid } }).then(res => {
        ElMessage.info("商品已添加到收藏夹！");
        console.log(res)

        // 处理返回的详细信息
      }).catch(error => {
        // 处理请求错误
        console.error(error);
      });
    },
  },

  mounted() {
    // 示例：假设从后端获取成功消息
    // 在实际应用中，您可能需要在某个操作成功后调用 showSuccessModal
    this.filteredItems = this.items; // 初始时显示所有商品
  },
};
</script>
