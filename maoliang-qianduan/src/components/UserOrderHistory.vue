<template>
<section class="legacy-page">
  <div >
    <div v-if="isLoggedIn">
      <div class="container">
        <router-link :to="{ name: 'ShowAllUsers' }">返回</router-link>
        <div class="centered-container">
          <h2>用户 {{ $route.query.username || $route.query.userid }} 的购买历史</h2>
        </div>
        <el-table :data="paginatedOrders" empty-text="暂无记录"><el-table-column label="ID" min-width="120"><template #default="{row: order}">{{ order.orderid }}</template></el-table-column>
<el-table-column label="地址" min-width="120"><template #default="{row: order}">{{ order.address }}</template></el-table-column>
<el-table-column label="电话" min-width="120"><template #default="{row: order}">{{ order.telephone }}</template></el-table-column>
<el-table-column label="收货人" min-width="120"><template #default="{row: order}">{{ order.recipientname || order.buyername }}</template></el-table-column>
<el-table-column label="数量" min-width="90"><template #default="{row: order}">{{ order.number }}</template></el-table-column>
<el-table-column label="商品ID" min-width="120"><template #default="{row: order}">{{ order.goodid }}</template></el-table-column>
<el-table-column label="订单状态" min-width="120"><template #default="{row: order}">{{ getOrderStatus(order.orderstate) }}</template></el-table-column></el-table>
        <div class="pagination">
          <button @click="goToPrevPage" :disabled="isPrevDisabled">上一页</button>
          <span>{{ currentPage }} / {{ totalPages }}</span>
          <button @click="goToNextPage" :disabled="isNextDisabled">下一页</button>
        </div>
      </div>
    </div>
    <div v-else>
      <span>您还未登录，请先<router-link to="/">登录</router-link></span>
    </div>
  </div>
</section>
</template>
<script>
import { mapGetters } from 'vuex';
import axios from 'axios';

export default {
  props: ['userId'],
  data() {
    return {
      isLoggedIn: true, // 应从 Vuex 或父组件动态获取
      orders: [],
      currentPage: 1,
      pageSize: 5,
      currentUser: {}, // 将 currentUser 初始化为一个空对象
    };
  },
  computed: {
    ...mapGetters(['isSeller', 'isBuyer']), // 确保这些 getter 在 Vuex store 中定义
    totalPages() {
      return Math.max(1, Math.ceil(this.orders.length / this.pageSize));
    },
    paginatedOrders() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      return this.orders.slice(start, end);
    },
    isPrevDisabled() {
      return this.currentPage === 1;
    },
    isNextDisabled() {
      return this.currentPage >= this.totalPages;
    },
  },
  methods: {
    async fetchUsrFromSession() {
      try {
        const response = await axios.get('/now-usr');
        this.currentUser = response.data;
        if (this.currentUser) {
          this.isLoggedIn = true;
        }
      } catch (error) {
        console.error('获取用户数据错误:', error);
        this.isLoggedIn = false;
      }
    },
    async fetchOrders() {
      try {
        // 在获取订单前先获取当前用户信息
        await this.fetchUsrFromSession();

        // 获取当前用户权限
        // Query the selected customer by the existing order endpoint.

        // 根据用户权限和用户ID获取订单列表
        const response = await axios.get('/order/showbuyerorderinfo-control', { params: { name: this.$route.query.username } });

        if (response.data) {
          this.orders = response.data.data || [];
          console.log("获取用户历史数据列表成功")
        } else {
          console.error("获取用户历史数据列表失败");
        }
      } catch (error) {
        console.error('请求用户历史数据列表错误', error);
      }
    },
    getOrderStatus(statusCode) {
      const statusMap = {
        '-1': '交易取消',
        '0': '等待客户下单',
        '1': '等待商家确认',
        '2': '等待备货确认',
        '3': '等待发货确认',
        '4': '商家已发货，等待交易确认',
        '5': '交易成功',
      };
      return statusMap[statusCode] || '未知状态';
    },
    goToPrevPage() {
      if (this.currentPage > 1) this.currentPage--;
    },
    goToNextPage() {
      if (this.currentPage < this.totalPages) this.currentPage++;
    },
  },
  mounted() {
    // 在组件挂载时获取订单数据
    this.fetchOrders();
  },
};
</script>
