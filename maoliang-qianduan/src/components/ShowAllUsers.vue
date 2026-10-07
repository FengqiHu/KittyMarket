<template>
<div class="section-heading"><div><h1>客户管理</h1><p>认识每一位热爱猫咪的朋友。</p></div><el-tag effect="plain" round>{{ users.length }} 位客户</el-tag></div><section class="surface table-surface"><el-table :data="paginatedUsers" empty-text="暂无客户"><el-table-column prop="userid" label="客户 ID" width="130" /><el-table-column label="客户" min-width="220"><template #default="{row}"><div class="customer-name"><el-avatar :size="36">{{ row.username.slice(0,1) }}</el-avatar><strong>{{ row.username }}</strong></div></template></el-table-column><el-table-column prop="phone" label="联系电话" min-width="170" /><el-table-column prop="address" label="收货地址" min-width="220" /><el-table-column label="操作" width="150"><template #default="{row}"><el-button link type="primary" @click="$router.push({name:'UserOrderHistory',query:{userid:row.userid,username:row.username}})">查看订单<el-icon><IconArrowRight /></el-icon></el-button></template></el-table-column></el-table><div class="table-pagination"><span class="muted">共 {{ users.length }} 位客户</span><el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="users.length" layout="prev, pager, next" background /></div></section>
</template>

<script>
import axios from "axios";

export default {
  data() {
    return {
      currentUser : null,
      currentPage: 1,
      pageSize: 5,
      users: [], // 存储所有用户
    };
  },
  computed: {
    isLoggedIn() {
      // 根据当前用户数据判断用户是否登录
      return !!this.currentUser;
    },
    paginatedUsers() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      return this.users.slice(start, end);
    },
    totalPages() {
      return Math.max(1, Math.ceil(this.users.length / this.pageSize));
    },
  },
  methods: {
    async fetchUsrFromSession() {
      try {
        // 发起 GET 请求到后端接口
        const response = await axios.get('/now-usr');
        // 解析响应数据
        const getUsr = response.data;
        // 更新组件的 currentUser 数据
        this.currentUser = getUsr;

        return true;
      } catch (error) {
        console.error('获取用户数据错误:', error);
        return false;
      }
    },
    goToPrevPage() {
      if (this.currentPage > 1) this.currentPage--;
    },
    goToNextPage() {
      if (this.currentPage < this.totalPages) this.currentPage++;
    },
    fetchUsers() {
      axios.post('/usr/all-buyer').then(response => {
        this.users = response.data.data;
        console.log(response.data);
      }).catch(error => {
        // 错误处理函数
        console.error('There was an error!', error);
      });
    }
  },
  mounted() {
    this.fetchUsrFromSession(); // 获取当前用户数据
    this.fetchUsers();
  },
};
</script>
