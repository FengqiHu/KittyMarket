<template>
  <div class="section-heading">
    <div><h1>我的猫咪</h1><p>添加、编辑或删除猫咪档案，记录每只猫咪的成长信息。</p></div>
    <div class="cat-heading-actions">
      <el-button @click="$router.push('/buyer-show-recommend')">查看专属推荐<el-icon><IconArrowRight /></el-icon></el-button>
      <el-button type="primary" :disabled="!ready" @click="add"><el-icon><IconPlus /></el-icon>添加猫咪</el-button>
    </div>
  </div>

  <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
  <div class="cat-profile-grid" v-loading="loading">
    <section class="surface" v-for="cat in cats" :key="cat.catid">
      <div class="cat-profile-head">
        <img src="@/assets/img/catbackground.png" alt="猫咪档案头像" />
        <div><h2>{{ cat.catname }}</h2><span class="muted">{{ cat.description || '暂无简介' }}</span></div>
      </div>
      <el-descriptions :column="2">
        <el-descriptions-item label="品种">{{ cat.catkind }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ cat.catage }} 岁</el-descriptions-item>
        <el-descriptions-item label="体重">{{ cat.catweight }} kg</el-descriptions-item>
        <el-descriptions-item label="生长状态">{{ stateLabel(cat.catstate) }}</el-descriptions-item>
        <el-descriptions-item label="档案编号">#{{ cat.catid }}</el-descriptions-item>
      </el-descriptions>
      <div class="cat-profile-actions">
        <el-button plain type="primary" @click="edit(cat)"><el-icon><IconEdit /></el-icon>编辑档案</el-button>
        <el-button plain type="danger" @click="remove(cat)">删除</el-button>
      </div>
    </section>
  </div>
  <el-empty v-if="!loading && ready && !cats.length" description="还没有猫咪档案">
    <el-button type="primary" @click="add">添加第一只猫咪</el-button>
  </el-empty>

  <el-dialog v-model="dialog" :title="editingId === null ? '添加猫咪' : '编辑猫咪档案'" width="480px" align-center>
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item label="名字" required><el-input v-model="form.catname" maxlength="20" show-word-limit placeholder="猫咪的名字" /></el-form-item>
      <el-form-item label="品种" required><el-input v-model="form.catkind" maxlength="20" show-word-limit placeholder="例如：布偶猫" /></el-form-item>
      <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="100" show-word-limit placeholder="记录猫咪的特点" /></el-form-item>
      <div class="cat-form-numbers">
        <el-form-item label="年龄（岁）" required><el-input-number v-model="form.catage" :min="0" :max="40" :precision="0" /></el-form-item>
        <el-form-item label="体重（kg）" required><el-input-number v-model="form.catweight" :min="0.1" :max="100" :precision="1" :step="0.1" /></el-form-item>
      </div>
      <el-form-item label="生长状态" required><el-select v-model="form.catstate" placeholder="请选择生长状态"><el-option v-for="option in states" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="dialog=false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script>
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';

const emptyForm = () => ({ catname: '', catkind: '', description: '', catage: 0, catweight: 0.1, catstate: 3 });

export default {
  data: () => ({
    cats: [], loading: false, ready: false, saving: false, error: '', dialog: false,
    editingId: null, form: emptyForm(),
    states: [
      { value: 1, label: '肥胖（计划减重）' }, { value: 2, label: '不活跃' },
      { value: 3, label: '已绝育成年猫' }, { value: 4, label: '未绝育成年猫' },
      { value: 5, label: '妊娠期' }, { value: 6, label: '哺乳期' }, { value: 7, label: '未成年' }
    ]
  }),
  created() { this.load(); },
  methods: {
    async load() {
      this.loading = true;
      this.error = '';
      try {
        const { data } = await axios.get('/cat/show-cat');
        if (data.page !== 'success') throw new Error(data.msg || '猫咪档案加载失败');
        this.cats = data.data || [];
        this.ready = true;
        if (this.$route.query.action === 'add') {
          this.add();
          this.$router.replace({ path: this.$route.path });
        }
      } catch (error) {
        this.ready = false;
        this.error = error.message || '猫咪档案加载失败';
      } finally { this.loading = false; }
    },
    stateLabel(value) { return this.states.find(state => state.value === value)?.label || '未知'; },
    add() { this.editingId = null; this.form = emptyForm(); this.dialog = true; },
    edit(cat) {
      this.editingId = cat.catid;
      this.form = {
        catname: cat.catname, catkind: cat.catkind, description: cat.description || '',
        catage: cat.catage, catweight: cat.catweight, catstate: cat.catstate
      };
      this.dialog = true;
    },
    async save() {
      if (this.saving) return;
      const payload = { ...this.form, catname: this.form.catname.trim(), catkind: this.form.catkind.trim(), description: this.form.description.trim() };
      if (!payload.catname || !payload.catkind) { ElMessage.warning('请填写猫咪名字和品种'); return; }
      if (!Number.isFinite(payload.catage) || !Number.isFinite(payload.catweight) || payload.catweight <= 0 || !payload.catstate) {
        ElMessage.warning('请填写有效的年龄、体重和生长状态'); return;
      }
      this.saving = true;
      try {
        const { data } = this.editingId === null
          ? await axios.post('/cat/add-cat', payload)
          : await axios.put(`/cat/update-cat/${this.editingId}`, payload);
        if (data.page !== 'success') throw new Error(data.msg || '保存失败，请重试');
        this.dialog = false;
        ElMessage.success(data.msg);
        await this.load();
      } catch (error) { ElMessage.error(error.message || '保存失败，请重试'); }
      finally { this.saving = false; }
    },
    async remove(cat) {
      try {
        await ElMessageBox.confirm(`确定删除“${cat.catname}”的档案吗？删除后无法恢复。`, '删除猫咪档案', {
          confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger', type: 'warning'
        });
      } catch { return; }
      try {
        const { data } = await axios.delete(`/cat/delete-cat/${cat.catid}`);
        if (data.page !== 'success') throw new Error(data.msg || '删除失败，请重试');
        ElMessage.success(data.msg);
        await this.load();
      } catch (error) { ElMessage.error(error.message || '删除失败，请重试'); }
    }
  }
};
</script>
