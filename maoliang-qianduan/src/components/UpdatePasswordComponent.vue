<template>
<div class="section-heading"><div><h1>账户设置</h1><p>更新登录密码，让你的账户更安心。</p></div></div><section class="surface narrow-surface"><el-form label-position="top" size="large" @submit.prevent="submitForm"><el-form-item label="当前密码"><el-input v-model="oldpwd" type="password" show-password required autocomplete="current-password" /></el-form-item><el-form-item label="新密码"><el-input v-model="newpwd" type="password" show-password required maxlength="15" autocomplete="new-password" /></el-form-item><el-form-item label="再次输入新密码"><el-input v-model="newpwd1" type="password" show-password required maxlength="15" autocomplete="new-password" /></el-form-item><el-alert v-if="error" :title="error" type="error" class="form-alert" /><el-button type="primary" native-type="submit">保存新密码</el-button><el-button @click="cancelUpdate">重置</el-button></el-form></section>
</template>

<script>
import { ElMessage } from 'element-plus';
export default {
    data() {
        return {
            isLoggedIn: true, // 这应该是从Vuex store或父组件获取的
            oldpwd: '',
            newpwd:'',
            newpwd1:'',
            credentials: {
            newpwd:'',
            newpwd1:''
      },
            message: null,
            error: null,
        };
    },
    methods: {
        validateForm() {
            if (this.newpwd.length > 15) {
                ElMessage.info("密码不能超过15个字符！");
                return false;
            }
            // // 检查旧密码是否正确
            // if (this.oldpwd !== this.userpwd) {
            //     this.error
            //     = "旧密码错误";
            //     return false;
            // }
            // // 检查新密码是否与旧密码相同
            // if (this.oldpwd === this.newpwd) {
            //     this.error = "新密码与旧密码一致";
            //     return false;
            // }

            // // 检查新密码是否与确认密码匹配
            // if (this.newpwd !== this.newpwd1) {
            //     this.error = "新密码与确认密码不一致";
            //     return false;
            // }

            // 如果所有检查都通过，返回true
            this.error = null;
            return true;

        },
        async submitForm() {
            // 先进行表单验证
            const isValid = this.validateForm();
            if (!isValid) return;
            // 如果验证通过，执行密码修改逻辑
            try {
                const credentials = {
                  oldpwd: this.oldpwd,
          newpwd: this.newpwd,
          newpwd1: this.newpwd1
        };
        const response = await fetch('/usr/changedpwd-control', {
          method: 'POST',
          body: JSON.stringify(credentials),
          headers: {
            'Content-Type': 'application/json'
          }
        });
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
    }catch (error) {
                console.error("修改密码失败:", error);
                this.error = "密码修改失败，请稍后再试。";
            }
        },
        cancelUpdate() {
            // 取消修改的逻辑
            // 例如：清空表单，跳转回上一页等
            this.oldpwd = '';
            this.newpwd = '';
            this.newpwd1 = '';
            this.error = null;
            this.message = null;
            // this.$router.push('/previous-page');
        }
    }
};
</script>
