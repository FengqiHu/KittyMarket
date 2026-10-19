<template>
  <div class="address-book" v-loading="loading">
    <div class="address-book-toolbar"><span class="muted">{{ selectable ? '选择本次订单的收货地址' : '管理常用收货地址，结算时可直接选择' }}</span><el-button type="primary" plain :disabled="loading || saving || disabled" @click="edit()"><el-icon><IconPlus /></el-icon>新增地址</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert"><el-button text @click="load()">重新加载</el-button></el-alert>
    <div class="address-list">
      <article v-for="item in addresses" :key="item.id" class="address-card" :class="{ 'is-selected': selectable && selectedId === item.id }">
        <label v-if="selectable" class="address-choice"><input v-model="selectedId" type="radio" :value="item.id" name="shipping-address" :disabled="loading || saving || disabled" :aria-label="'寄送至 ' + item.recipient + '，' + item.address" @change="select(item)"><span>寄送至此</span></label>
        <div class="address-card-content"><div class="address-recipient"><strong>{{ item.recipient }}</strong><span>{{ item.telephone }}</span><el-tag v-if="item.isDefault" size="small" effect="plain">默认地址</el-tag></div><p>{{ item.address }}</p></div>
        <div class="address-card-actions"><el-button v-if="!item.isDefault" link type="primary" :disabled="saving || disabled" @click="makeDefault(item)">设为默认</el-button><el-button link type="primary" :disabled="saving || disabled" @click="edit(item)">编辑</el-button><el-button link type="danger" :disabled="saving || disabled" @click="remove(item)">删除</el-button></div>
      </article>
    </div>
    <el-empty v-if="!loading && !error && !addresses.length" description="还没有收货地址"><el-button type="primary" @click="edit()">添加收货地址</el-button></el-empty>
    <el-dialog v-model="dialogOpen" :title="form.id ? '编辑收货地址' : '新增收货地址'" width="520px" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" append-to-body destroy-on-close>
      <el-form label-position="top" @submit.prevent.stop="save">
        <el-form-item label="收货人"><el-input v-model="form.recipient" required maxlength="10" placeholder="收货人姓名，最多 10 个字符" :disabled="saving || disabled" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.telephone" required maxlength="11" placeholder="11 位联系电话" :disabled="saving || disabled" /></el-form-item>
        <el-form-item label="详细地址"><el-input v-model="form.address" required maxlength="99" placeholder="省市、街道及详细门牌号" :disabled="saving || disabled" /></el-form-item>
        <label class="address-default-choice"><input v-model="form.isDefault" type="checkbox" :disabled="saving || disabled">设为默认收货地址</label>
        <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="form-alert" />
        <div class="address-dialog-actions"><el-button :disabled="saving || disabled" @click="dialogOpen = false">取消</el-button><el-button native-type="submit" type="primary" :loading="saving">保存地址</el-button></div>
      </el-form>
    </el-dialog>
  </div>
</template>
<script>
import { ElMessage, ElMessageBox } from 'element-plus';
import { accountRequest } from '../utils/accountApi';
const emptyForm = () => ({ id: null, recipient: '', telephone: '', address: '', isDefault: false });
export default {
  props: { selectable: Boolean, disabled: Boolean },
  emits: ['select', 'busy'],
  data: () => ({ addresses: [], selectedId: null, loading: true, saving: false, dialogOpen: false, error: '', formError: '', form: emptyForm() }),
  computed: { busy() { return this.loading || this.saving; } },
  watch: {
    disabled(value) { if (value) this.dialogOpen = false; },
    busy: { immediate: true, handler(value) { this.$emit('busy', value); } },
  },
  created() { this.load(); },
  methods: {
    async load(preferredId) {
      this.loading = true; this.error = '';
      try {
        this.addresses = await accountRequest('get', '/addresses');
        const chosen = this.addresses.find(item => item.id === (preferredId || this.selectedId)) || this.addresses.find(item => item.isDefault) || this.addresses[0] || null;
        this.selectedId = chosen?.id || null;
        if (this.selectable) this.$emit('select', chosen);
      } catch (error) { this.error = error.message; if (this.selectable) this.$emit('select', null); }
      finally { this.loading = false; }
    },
    select(item) { if (!this.disabled) this.$emit('select', item); },
    edit(item) { if (this.disabled) return; this.form = item ? { ...item } : emptyForm(); this.formError = ''; this.dialogOpen = true; },
    async save() {
      if (this.saving || this.disabled) return;
      this.formError = '';
      if (!this.form.recipient.trim() || !this.form.address.trim()) { this.formError = '请填写收货人和详细地址'; return; }
      if (!/^[0-9]{11}$/.test(this.form.telephone.trim())) { this.formError = '联系电话需要是 11 位数字'; return; }
      this.saving = true;
      try {
        const saved = await accountRequest(this.form.id ? 'put' : 'post', '/addresses' + (this.form.id ? '/' + this.form.id : ''), this.form);
        this.dialogOpen = false;
        await this.load(this.form.id ? this.selectedId : saved.id);
        ElMessage.success('地址已保存');
      } catch (error) { this.formError = error.message; }
      finally { this.saving = false; }
    },
    async makeDefault(item) {
      if (this.disabled || this.saving) return;
      this.saving = true;
      try { await accountRequest('put', '/addresses/' + item.id + '/default'); await this.load(); ElMessage.success('默认地址已更新'); }
      catch (error) { ElMessage.error(error.message); }
      finally { this.saving = false; }
    },
    async remove(item) {
      if (this.disabled || this.saving) return;
      try { await ElMessageBox.confirm('删除这条收货地址？已提交订单的收货信息会保留。', '删除地址', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }); }
      catch { return; }
      this.saving = true;
      try { await accountRequest('delete', '/addresses/' + item.id); await this.load(); ElMessage.success('地址已删除'); }
      catch (error) { ElMessage.error(error.message); }
      finally { this.saving = false; }
    },
  },
};
</script>
<style scoped>
.address-book-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; }
.address-list { display: grid; gap: 12px; }
.address-card { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; border: 1px solid var(--line); padding: 18px; border-radius: 6px; }
.address-card.is-selected { border-color: var(--orange); background: var(--el-color-primary-light-9); }
.address-choice { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 12px; }
.address-choice input, .address-default-choice input { accent-color: var(--orange); }
.address-card-content { flex: 1; min-width: 180px; overflow-wrap: anywhere; }
.address-recipient { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.address-recipient > span { color: var(--muted); }
.address-card-content p { margin: 8px 0 0; line-height: 1.7; }
.address-card-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.address-card-actions .el-button { margin-left: 0; }
.address-default-choice { display: flex; align-items: center; gap: 8px; margin: 8px 0 20px; }
.address-dialog-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
@media (max-width: 640px) { .address-card { align-items: flex-start; padding: 14px; } .address-card-content { min-width: 0; flex-basis: 100%; } .address-recipient { gap: 8px; } }
</style>
