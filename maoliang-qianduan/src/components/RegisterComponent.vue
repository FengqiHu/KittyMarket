<template>
<AuthLayout><h2 class="auth-title">注册账号</h2><p class="auth-subtitle">选择账号类型并填写注册信息。</p>
<el-form label-position="top" size="large" @submit.prevent="handleRegister">
<el-form-item label="你的身份"><el-radio-group v-model="form.userType" @change="updateUserType"><el-radio-button value="0">我是买家</el-radio-button><el-radio-button value="1">我是卖家</el-radio-button></el-radio-group></el-form-item>
<div class="form-grid"><el-form-item label="用户名"><el-input v-model="form.username" required maxlength="10" placeholder="最多 10 个字符" autocomplete="username" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" required type="password" show-password maxlength="15" autocomplete="new-password" placeholder="设置登录密码" /></el-form-item>
<el-form-item label="密保问题"><el-input v-model="form.question" required maxlength="50" placeholder="例如：第一只猫咪的名字？" /></el-form-item><el-form-item label="密保答案"><el-input v-model="form.answer" required maxlength="30" placeholder="请输入答案" /></el-form-item></div>
<el-form-item label="联系电话"><el-input v-model="form.phone" required maxlength="11" placeholder="请输入 11 位联系电话" /></el-form-item><el-form-item v-if="!isSeller" label="收货地址"><el-input v-model="form.address" required maxlength="99" placeholder="请输入默认收货地址" /></el-form-item>
<el-button type="primary" native-type="submit" class="full-width" :loading="loading">创建{{ isSeller ? '卖家' : '买家' }}账号</el-button><div class="auth-register">已有账号？<router-link to="/">返回登录</router-link></div>
</el-form></AuthLayout>
</template>

<script>
import { ElMessage } from 'element-plus';



export default {
  data() {
    return {
      isSeller: false,
      loading: false,
      form: {
        userType: '0',
        username: '',
        phone: '',
        address: '',
        password: '',
        question: '',
        answer: ''
      }
    };
  },
  methods: {
    async handleRegister(){
      if (!/^\d{11}$/.test(this.form.phone)) { ElMessage.warning('请输入 11 位联系电话'); return; }
      this.loading = true;
      try{
        const credentials = {
          username: this.form.username,
          password: this.form.password,
          question: this.form.question,
          answer: this.form.answer,
          power: Number(this.form.userType),
          address: this.form.address,
          phone: this.form.phone

        };

        const response = await fetch('/usr/register-control', {
          method: 'POST',
          body: JSON.stringify(credentials),
          headers: {
            'Content-Type': 'application/json'
          }
        });
        const responseData = await response.json();

        if (responseData.msg !== 'success') {
          ElMessage.error(responseData.msg);
        } else {
          console.log("success");
          console.log(responseData.msg);
          ElMessage.success('注册成功，请登录');
          this.$router.push('/'); // 跳转到登录页面
        }
      }catch (error){
        ElMessage.error('注册失败，请稍后重试');
        // this.error = 'register fail!';
      } finally { this.loading = false; }
    },
    updateUserType(userType) {
      this.isSeller = userType === '1';
      this.form.userType = userType;
    }
  },
  created() {
    this.updateUserType(this.$route.query.registeruser === '1' ? '1' : '0');
  },

};
</script>
