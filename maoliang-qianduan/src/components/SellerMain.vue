<template>
<div v-if="isLoggedIn"><router-view /></div><el-skeleton v-else :rows="6" animated />
</template>

<script>
import axios from "axios";

export default {
    data() {
        return {
          // 您的数据属性
          currentUser :null
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
            // console.log(this.currentUser);
            // console.log(!this.isLoggedIn);
            // console.log(!this.isSeller);
            // console.log(!this.isLoggedIn || !this.isSeller);
            // console.log("ss");
            if (!this.isLoggedIn || !this.isSeller) {
              this.$router.push('/');
            }
            return true;
          } catch (error) {
            console.error('获取用户数据错误:', error);
            return false;
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
    },
    mounted() {
      console.log('SellerMain 组件已挂载');
    },
};
</script>
