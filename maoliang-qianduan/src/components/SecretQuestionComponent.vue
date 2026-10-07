<template>
<AuthLayout><h2 class="auth-title">还记得这个答案吗？</h2><p class="auth-subtitle">{{ storedUsr.username }}，请回答你设置的密保问题。</p><el-alert :title="storedUsr.question || '请先填写需要找回的账号'" :closable="false" type="info" class="form-alert" /><el-form label-position="top" size="large" @submit.prevent="submitSecretAnswer"><el-form-item label="密保答案"><el-input v-model="secretAnswer" required placeholder="请输入你的答案" /></el-form-item><el-button type="primary" native-type="submit" class="full-width">验证答案</el-button></el-form><el-alert v-if="error" :title="error" type="error" class="form-alert" /><div class="auth-register"><router-link to="/">返回登录</router-link></div></AuthLayout>
</template>

<script>
import { ElMessage } from 'element-plus';
import axios from "axios";

export default {
  data() {
    return {
      storedUsr: JSON.parse(sessionStorage.getItem("forgetUsr") || "{}"), // 防止getItem返回null时，JSON.parse报错
      secretAnswer: "",
      error: "",
    };
  },
  methods: {
    async submitSecretAnswer() {
      try {
        const answerData = {
          answer: this.secretAnswer,
        };
        const response = await axios.post("/usr/answer-control", answerData, {
          headers: {
            "Content-Type": "application/json",
          },
        });
        const responseData = await response;
        if (responseData.data.msg === "rightAnswer") {
          ElMessage.info(`答案正确！您的密码是：${this.storedUsr.pwd}`);
          this.$router.push("/"); // 跳转到登录页面
        } else if (responseData.data.msg === "badAnswer") {
          ElMessage.info("答案错误！请重新输入密保答案！");
        } else {
          ElMessage.info("System Wrong!");
        }
      } catch (err) {
        this.error = "回答密保问题时出错";
        // 可以根据错误类型决定是否跳转
      }
    },
  },
};
</script>
