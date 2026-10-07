<template>
<div class="section-heading"><div><h1>猫粮推荐</h1><p>根据猫咪的品种、年龄和体重，找到适合它的猫粮。</p></div><el-button @click="fetchRecommendedCatFood"><el-icon><IconRefresh /></el-icon>刷新推荐</el-button></div><section class="recommend-banner"><el-icon><IconCollection /></el-icon><div><strong>按猫咪档案匹配猫粮</strong><p>每日建议喂食量仅供参考，请结合猫咪实际情况调整。</p></div><el-button @click="$router.push('/buyer-show-cat')">查看猫咪档案<el-icon><IconArrowRight /></el-icon></el-button></section><div class="product-grid"><article v-for="item in items" :key="item.goodid" class="product-card"><button class="product-cover" @click="postToBuyerShop(item.goodid)"><ProductMedia :path="item.picture" :alt="item.goodname" /><span class="product-category">推荐商品</span></button><div class="product-card-body"><h3>{{ item.goodname }}</h3><p>建议每日喂食 {{ Number(item.weight).toFixed(1) }} g</p><div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span><el-button size="small"  @click="postToBuyerShop(item.goodid)">查看详情</el-button></div></div></article></div><el-empty v-if="!items.length" description="暂无匹配推荐，请先确认猫咪档案或浏览更多商品"><el-button @click="$router.push('/buyer')">浏览商品</el-button></el-empty>
</template>

<script>
import { mediaUrl } from '../utils/media';
import { mapActions } from 'vuex';
import axios from "axios";

export default {
  data() {
    return {
      items: [], // 存储猫粮数据
      username: '', // 用户名
      currentUser: null, // 当前用户信息
    };
  },
  created() {
    this.fetchUserData();
  },
  computed: {
    isLoggedIn() {
      return !!this.currentUser;
    },
  },
  methods: {
    ...mapActions(['logout']),
    getImageUrl(picturePath) { return mediaUrl(picturePath); },
    async fetchUserData() {
      try {
        const response = await axios.get('/now-usr');
        const user = response.data;
        if (user) {
          this.currentUser = user;
          this.username = user.username;
          this.fetchRecommendedCatFood(); // 获取推荐猫粮数据
        }
      } catch (error) {
        console.error('获取用户数据错误:', error);
      }
    },
    async fetchRecommendedCatFood() {
      try {
        if (this.isLoggedIn && this.currentUser && this.currentUser.userid) {
          const response = await axios.get(`/cat/show-recommend/${this.currentUser.userid}`);
          const { data } = response;
          if (data && data.data) {
            this.items = data.data.slice(0, 3); // 取前三个猫粮
          }
        }
      } catch (error) {
        console.error('获取推荐猫粮数据错误:', error);
      }
    },
    handleLogout() {
      this.logout();
      this.$router.push('/');
    },
    navigateTo(routeName) {
      this.$router.push({ name: routeName });
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
  },
};
</script>
