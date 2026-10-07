<template>
<section class="legacy-page">
  <el-alert v-if="$route.query.orderid && orders.some(order => order.orderid === Number($route.query.orderid) && order.orderstate === 1)" :title="'订单 #' + $route.query.orderid + ' 已创建，等待商家确认。'" type="success" show-icon class="form-alert" />

  <div >
  <div v-if="isLoggedIn">


    <!-- 右侧内容区域 -->
    <div class="right" >
      <div class="container">
        <router-link :to="{ name: 'BuyerMain' }">
          返回
        </router-link>
        <div class="centered-container">
          <h2>历史下单记录</h2>
        </div>
        <el-table :data="paginatedOrders" empty-text="暂无记录"><el-table-column label="ID" min-width="120"><template #default="{row: order}">{{ order.orderid }}</template></el-table-column>
<el-table-column label="地址" min-width="120"><template #default="{row: order}">{{ order.address }}</template></el-table-column>
<el-table-column label="电话" min-width="120"><template #default="{row: order}">{{ order.telephone }}</template></el-table-column>
<el-table-column label="收货人" min-width="120"><template #default="{row: order}">{{ order.recipientname || order.buyername }}</template></el-table-column>
<el-table-column label="数量" min-width="90"><template #default="{row: order}">{{ order.number }}</template></el-table-column>
<el-table-column label="商品ID" min-width="120"><template #default="{row: order}">{{ order.goodid }}</template></el-table-column>
<el-table-column label="操作" min-width="170"><template #default="{row: order}">
              <button v-if="order.orderstate === 4" class="green-btn"
                      @click="handleOrderAction(order.orderid, 'confirmCompletion')"
                      >确认交易完成</button>
              <button v-if="order.orderstate >= 0 && order.orderstate < 4" class="red-btn"
                      @click="handleOrderAction(order.orderid, 'cancel')"
                      >取消订单</button>
              <span v-if="order.orderstate > 4 || order.orderstate === -1">无法操作订单</span>
            </template></el-table-column>
<el-table-column label="订单状态" min-width="120"><template #default="{row: order}">{{ getOrderStatus(order.orderstate) }}</template></el-table-column></el-table>
        <div class="pagination">
          <button @click="goToPrevPage" :disabled="isPrevDisabled" class="prev">上一页</button>
          <span>{{ currentPage }} / {{ totalPages }}</span>
          <button @click="goToNextPage" :disabled="isNextDisabled" class="next">下一页</button>
        </div>
      </div>
    </div>
  </div>
  <div v-else>
    <!-- 用户未登录时显示的内容 -->
    <div class="else">
      您还未登录，请先<router-link to="/">登录</router-link>
    </div>
  </div>
  </div>
</section>
</template>
<script>
import { ElMessage } from 'element-plus';
import { mapGetters, mapActions } from 'vuex';
import axios from 'axios'; // 确保已经安装并导入axios
export default {
  data() {
    return {
      isLoggedIn: true, // 根据实际登录状态设置
      currentUser: null,
      orders: [
      ],
      currentPage: 1,
      pageSize: 5,
      totalItems: 8,
    };
  },
  methods: {
    ...mapActions(['logout']),
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
    goBack() {
      this.$router.push({ name: 'BuyerMain' });
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
        // 添加其他状态映射
      };
      return statusMap[statusCode] || '未知状态';
    },
    handleOrderAction(orderId, action) {
      // 根据 action 类型处理不同的订单操作
      switch (action) {
        case 'confirmCompletion':
          this.confirmOrder(orderId);
          break;
        case 'cancel':
          this.cancelOrder(orderId);
          break;
          // 可以根据需要添加其他操作
        default:
          console.log('无效的操作');
      }
    },
    confirmOrder(orderId) {
      const order = this.orders.find(o => o.orderid === orderId);
      if (order) {
        // 这里将状态加 1 来模拟确认订单
        let updatedOrderState = order.orderstate + 1;
        // 发送异步请求到服务器以更新订单状态
        axios.post('/order/buyerhistoryconfirmorder-control', null, { params: { orderid: orderId, orderstate: updatedOrderState } })
            .then(response => {
              if (response.data && response.data.msg === '确认订单成功') {
                // 如果成功，更新本地订单状态
                order.orderstate = updatedOrderState;
                ElMessage.info("该订单阶段确认成功！");
                // 可能还需要重新获取订单列表
              } else {
                ElMessage.info("该订单阶段确认失败！");
              }
            })
            .catch(error => {
              console.error('确认订单出错:', error);
            });
      }



    },
    cancelOrder(orderId) {

      const order = this.orders.find(o => o.orderid === orderId);
      if (order) {
        // 发送异步请求到服务器以更新订单状态
        axios.post('/order/deleteorder-control', null, { params: { orderid: orderId,orderstate:-1} })
            .then(response => {
              if (response.data && response.data.msg === '取消订单成功') {
                // 如果成功，更新本地订单状态
                order.orderstate = -1;
                ElMessage.info("该订单取消成功！");
                // 可能还需要重新获取订单列表
              } else {
                ElMessage.info("该订单取消失败！");
              }
            })
            .catch(error => {
              console.error('取消订单出错:', error);
            });
      }
    },
    goToPrevPage() {
      if (this.currentPage > 1) {
        this.currentPage--;
        this.fetchOrders(); // 实现该方法以从服务器获取订单
      }
    },
    goToNextPage() {
      if (this.currentPage < this.totalPages) {
        this.currentPage++;
        this.fetchOrders(); // 实现该方法以从服务器获取订单
      }
    },
    async fetchUsrFromSession() {
      try {
        const response = await axios.get('/now-usr');
        this.currentUser = response.data;
        this.isLoggedIn = !!this.currentUser;
      } catch (error) {
        console.error('获取用户数据错误:', error);
        this.isLoggedIn = false;
      }
    },
    async fetchOrders() {
      console.log(1)
      await this.fetchUsrFromSession();
      if (!this.currentUser) { this.orders = []; return; }
      axios.get('/order/showbuyerorderinfo-control', {
        params: {
          name:this.currentUser.username
        }
      })
          .then(response => {

            if (response.data && response.data.data) {
              this.orders = response.data.data; // 假设这是包含所有订单的数组

              console.log("获取意向订单数据列表成功");
            } else {
              console.error("获取意向订单数据列表失败");
            }
          })
          .catch(error => {
            console.error('获取意向订单数据列表错误', error);
          });
      console.log(2)
    }
  },
  computed: {
    ...mapGetters(['isSeller', 'isBuyer']),
    username() {
      // 从 Vuex store 获取用户名
      return this.$store.state.admin ? this.$store.state.admin.username : '未登录';
    },
    totalPages() {
      if (1 > Math.ceil(this.orders.length / this.pageSize)) return 1;
      return Math.max(1, Math.ceil(this.orders.length / this.pageSize));
    },
    paginatedOrders() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      return this.orders.slice(start, end);
    },
    isSeller() {
      // 您可以根据实际情况判断用户是否是卖家
      return this.$store.getters.isSeller;
    },
    isPrevDisabled() {
      return this.currentPage === 1;
    },

    isNextDisabled() {
      return this.currentPage === this.totalPages;
    },
  },
  mounted() {
    this.fetchOrders();
    this.filteredUsers = this.orders;
  },
};
</script>
