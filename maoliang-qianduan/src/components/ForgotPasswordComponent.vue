<template>
<AuthLayout><h2 class="auth-title">找回密码</h2><p class="auth-subtitle">填写账号，通过密保问题验证身份。</p><el-form label-position="top" size="large" @submit.prevent="submitForgetPassword"><el-form-item label="账号"><el-input v-model="forgetName" required placeholder="请输入需要找回的账号" /></el-form-item><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button type="primary" native-type="submit" class="full-width">下一步<el-icon class="icon-after"><IconArrowRight /></el-icon></el-button></el-form><div class="auth-register"><router-link to="/">返回登录</router-link></div></AuthLayout>
</template>

<script>
import { ElMessage } from 'element-plus';
import axios from "axios";
export default {
  data() {
    return {
      forgetName: "",
      error: null,
    };
  },
  methods: {
    async submitForgetPassword() {
      // 实现找回密码的逻辑
      try {
        // 假设请求成功，并获取到了用户的密保问题等信息
        const postData = {
          username: this.forgetName,
        };
        const response = await axios.post("/usr/forgetPwd-control", postData, {
          headers: { "Content-Type": "application/json" },
        });
        const responseData = await response;
        if (responseData.data.msg === "answer") {
          // 跳转到密保问题回答页面，并存储传回的用户对象
          ElMessage.info("找到用户！");
          console.log(response.data.data);
          sessionStorage.setItem(
            "forgetUsr",
            JSON.stringify(response.data.data)
          );
          this.$router.push({ name: "SecretQuestion" });
        } else if (responseData.data.msg === "noName") {
          ElMessage.info("找不到该用户！");
        } else {
          ElMessage.info("System Wrong!");
        }
      } catch (err) {
        console.log(err);
        this.error = "找回密码时出错";
      }
    },
  },
};
</script>
