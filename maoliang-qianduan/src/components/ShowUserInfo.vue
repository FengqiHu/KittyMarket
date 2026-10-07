<template>
<section class="legacy-page">
  <div v-if="isLoggedIn">
    <div id="a">
    <div class="container">
      <div class="form-group">
        <h2>当前意向订单</h2>
      </div>
      <table   >
        <tr>
          <th>ID</th>
          <th>地址</th>
          <th>电话</th>
          <th>收货人</th>
          <th>数量</th>
          <th>商品ID</th>
          <th>操作</th>
          <th>订单状态</th>
        </tr>
        <tr v-for="(order, index) in paginatedItems" :key="index">
          <td>{{ order.orderid }}</td>
          <td>{{ order.address }}</td>
          <td>{{ order.telephone }}</td>
          <td>{{ order.recipientname || order.buyername }}</td>
          <td>{{ order.number }}</td>
          <td>{{ order.goodid }}</td>
          <td>
            <button v-if="order.orderstate === 1"
                    @click="confirmOrder(order.orderid)"
                    class="confirm-btn">确认订单</button>
            <button v-else-if="order.orderstate === 2"
                    @click="confirmOrder(order.orderid)"
                    class="stock-btn">确认备货</button>
            <button v-else-if="order.orderstate === 3"
                    @click="confirmOrder(order.orderid)"
                    class="shipment-btn">确认发货</button>
            <button v-else-if="order.orderstate === 4"
                    class="delivery-btn">发货完成</button>
            <button v-if=" order.orderstate < 4 && order.orderstate > 0" @click="cancelOrder(order.orderid)" class="red-btn">取消订单</button>
            <span v-if="order.orderstate < 0 || order.orderstate > 4">无法操作订单</span>
          </td>
          <td>
            {{ getOrderStatus(order.orderstate) }}
          </td>
        </tr>
      </table>
      <div class="pagination">
        <button @click="goToPrevPage" :disabled="isPrevDisabled" class="prev">上一页</button>
        <span>第{{ currentPage }}/{{ totalPages }}页</span>
        <button @click="goToNextPage" :disabled="isNextDisabled" class="next">下一页</button>
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
import { ElMessage } from 'element-plus';
import axios from 'axios';
export default {
  data() {
    return {
      isLoggedIn: true, // 应根据实际登录逻辑设置
      orders: [],
      currentPage: 1,
      itemsPerPage: 5,
      // 假设 totalItems 由后端提供或计算得出
      currentUser: {},
    };
  },
  methods: {
    getOrderStatus(state) {
      // 根据状态返回订单状态描述
      // 实际逻辑可能需要调整
      const statusMap = {
        0: '等待客户下单',
        1: '客户已确认，商家待确认',
        2: '商家已确认，等待商家备货',
        3: '备货已完成，等待商家发货',
        4: '发货已完成，等待买家确认交易完成',
        5: '交易已完成', // 假设状态 5 表示交易已完成
      };
      return statusMap[state] || '订单被取消';
    },
    handleOrderAction(orderId, action) {
      // 根据 action 类型处理不同的订单操作
      switch (action) {
        case 'wait':
          // 等待买家确认的逻辑
          break;
        case 'confirm':
          this.confirmOrder(orderId);
          break;
        case 'prepare':
          // 确认备货的逻辑
          break;
        case 'ship':
          // 确认发货的逻辑
          break;
        case 'cancel':
          this.cancelOrder(orderId);
          break;
          // 其他情况...
      }
    },
    confirmOrder(orderId) {
      // 找到对应的订单
      const order = this.orders.find(o => o.orderid === orderId);
      if (order) {
        // 这里将状态加 1 来模拟确认订单
        let updatedOrderState = order.orderstate + 1;
        // 发送异步请求到服务器以更新订单状态
        axios.post('/order/confirmorder-control', null, { params: { orderid: orderId, orderstate: updatedOrderState } })
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
      // 找到对应的订单
      const order = this.orders.find(o => o.orderid === orderId);
      if (order) {
        // 发送异步请求到服务器以更新订单状态
        axios.post('/order/deleteorder-control', null, { params: { orderid: orderId, orderstate: -1 } })
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
    fetchOrders() {
      // 实现从服务器获取订单数据的逻辑
      axios.get('/order/showorderinfo-control')
          .then(response => {
            console.log(response.data.data); // 确保这里是你想要的数据结构
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
    },
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
    }
  },
  computed: {
    paginatedUsers() {
      const start = (this.currentPage - 1) * this.itemsPerPage;
      const end = start + this.itemsPerPage;
      return this.filteredUsers.slice(start, end);
    },
    // 计算总页数
    totalPages() {
      return Math.max(1, Math.ceil(this.orders.length / this.itemsPerPage));
    },
    isPrevDisabled() {
      return this.currentPage <= 1;
    },
    isNextDisabled() {
      return this.currentPage >= this.totalPages;
    },
    filteredOrders() {
      // 这里过滤掉状态为 -1 的订单，即已取消的订单
      return this.orders.filter(order => order.orderstate !== -2);
    },
    paginatedItems() {
      const start = (this.currentPage - 1) * this.itemsPerPage;
      const end = this.currentPage * this.itemsPerPage;
      return this.orders.slice(start, end);
    },
  },
  mounted() {
    this.fetchUsrFromSession().then(() => {
      if (this.isLoggedIn) {
        this.fetchOrders();
      }
    });
  },
  created() {
    this.fetchOrders(); // 初始加载订单数据
  },
}
</script>
