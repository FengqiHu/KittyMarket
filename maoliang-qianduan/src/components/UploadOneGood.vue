<template>
<div class="section-heading"><div><h1>发布商品</h1><p>填写商品信息并上传图片或视频。</p></div><el-button @click="goBack">返回商品管理</el-button></div><form class="publish-layout" @submit.prevent="submitForm"><section class="surface upload-surface"><h3>商品图片与视频</h3><div id="preview" class="upload-preview"><template v-if="currentFile"><img v-if="isImage(currentFile)" :src="getURL(currentFile)" alt="上传预览" /><video v-else controls :src="getURL(currentFile)"></video></template><div v-else class="media-fallback"><el-icon><IconPicture /></el-icon><p>添加商品图片或视频</p><small>PNG / JPG / MP4，单个文件不超过 10MB</small></div></div><div class="media-controls"><el-button circle aria-label="上一张" @click="prevImage"><el-icon><IconArrowLeft /></el-icon></el-button><span class="muted">{{ selectedFiles.length ? currentPreviewIndex + 1 : 0 }} / {{ selectedFiles.length }}</span><el-button circle aria-label="下一张" @click="nextImage"><el-icon><IconArrowRight /></el-icon></el-button></div><input type="file" ref="fileInput" :disabled="isFileInputDisabled" @change="handleFileChange" accept="image/png,image/jpeg,video/mp4" multiple hidden /><el-button type="primary" plain class="full-width" :disabled="isFileInputDisabled" @click="triggerFileInput"><el-icon><IconUpload /></el-icon>选择文件（最多 3 个）</el-button><div class="upload-file" v-for="(file,index) in selectedFiles" :key="index"><span>{{ file.name }}</span><el-button text type="danger" aria-label="移除文件" @click="removeFile(index)"><el-icon><IconClose /></el-icon></el-button></div></section><section class="surface"><h3>基本信息</h3><el-form label-position="top"><div class="form-grid"><el-form-item label="商品分类"><el-select v-model="selectedKind" @change="updateSubcategories"><el-option v-for="kind in kinds" :key="kind.value" :value="kind.value" :label="kind.text" /></el-select></el-form-item><el-form-item label="商品子类"><el-select v-model="selectedSubkind"><el-option v-for="kind in subkinds" :key="kind" :value="kind" :label="kind" /></el-select></el-form-item></div><el-form-item label="商品名称"><el-input v-model="goodName" required maxlength="20" placeholder="请输入商品名称" show-word-limit /></el-form-item><div class="form-grid"><el-form-item label="价格（元）"><el-input-number v-model="price" :min="0.01" :precision="2" controls-position="right" /></el-form-item><el-form-item label="库存（件）"><el-input-number v-model="stock" :min="1" :precision="0" controls-position="right" /></el-form-item></div><el-form-item label="商品描述"><el-input v-model="description" type="textarea" :rows="3" required maxlength="100" show-word-limit placeholder="介绍它的特点和适用场景" /></el-form-item><el-divider content-position="left">营养与适用范围</el-divider><div class="form-grid"><el-form-item label="卡路里（cal/g）"><el-input v-model="calorie" required placeholder="例如：3.4" /></el-form-item><el-form-item label="适用品种"><el-input v-model="catkind" required placeholder="例如：波斯猫,布偶猫" /></el-form-item><el-form-item label="适用体重（kg）"><el-input v-model="catweight" required placeholder="例如：1-7" /></el-form-item><el-form-item label="适用年龄（岁）"><el-input v-model="catage" required placeholder="例如：1-10" /></el-form-item></div></el-form><div class="form-actions"><el-button @click="goBack">取消</el-button><el-button type="primary" native-type="submit"><el-icon><IconCheck /></el-icon>确认发布</el-button></div></section></form>
</template>

<script>
import { ElMessage } from 'element-plus';
export default {
  data() {
    return {
      products: [], // 用于存储商品的数组
      isLoggedIn: true, // 这里应从Vuex store或父组件获取
      goodName: '',
      price: undefined,
      stock: undefined,
      description: '',
      calorie: '',
      catkind: '',
      catweight: '',
      catage: '',
      selectedFiles: [],
      selectedKind: '猫咪主粮',  // 设置默认的商品大类
      selectedSubkind: '',       // 商品子类的初始值将在mounted中设置
      currentPreviewIndex: 0,    // 当前预览文件的索引
      kinds: [
        { value: '猫咪主粮', text: '猫咪主粮' },
        { value: '猫咪零食', text: '猫咪零食' },
        { value: '猫咪日用', text: '猫咪日用' }
      ],
      subkinds: [],
      errors: {
        goodName: null,
        price: null,
        stock: null,
        description: null,
        calorie: null,
        catkind: null,
        catweight: null,
        catage: null
      },
      currentUser:null,
    };
  },
  methods: {

    updateSubcategories() {
      // 根据selectedKind更新subkinds数组
      if (this.selectedKind === '猫咪主粮') {
          this.subkinds = ['猫干粮', '猫湿粮'];
        } else if (this.selectedKind === '猫咪零食') {
          this.subkinds = ['饼干', '罐头', '猫条'];
        } else if (this.selectedKind === '猫咪日用') {
          this.subkinds = ['猫砂盆', '猫小窝', '猫沙发', '清洁除味'];
        }
        // 设置选中的子类为新数组的第一个元素
        if (this.subkinds.length > 0) {
          this.selectedSubkind = this.subkinds[0];
        } else {
          this.selectedSubkind = '';
      }
    },
    async submitForm() {
      // 在这里处理表单提交逻辑
      // 验证商品名称长度
      if (this.goodName.length > 20) {
        ElMessage.info("商品名称不能超过20个字符");
        return;
      }

      // 验证价格为数字
      if (isNaN(this.price)) {
        ElMessage.info("价格需要输入数字");
        return;
      }

      // 验证库存为数字
      if (isNaN(this.stock)) {
        this.errors.stock = "库存需要输入数字";
        return;
      }

      // 验证商品描述长度
      if (this.description.length > 100) {
        ElMessage.info("商品描述不能超过100个字符");
        return;
      }

      // 验证至少上传一个文件
      if (this.selectedFiles.length === 0) {
        ElMessage.info("请上传至少一个文件");
        return;
      }

      // 验证卡路里
      const calorieValue = parseFloat(this.calorie);
      if (isNaN(calorieValue) || calorieValue <= 0 || calorieValue >= 100) {
        ElMessage.info('卡路里需为大于0小于100的数字');
        return;
      }

      // 验证适用品种
      if (!this.catkind.trim().match(/^[\u4e00-\u9fa5,]+$/)) {
        ElMessage.info('适用品种格式不正确');
        return;
      }

      // 验证适用体重
      if (!this.catweight.trim().match(/^\d+(\.\d+)?-\d+(\.\d+)?$/)) {
        ElMessage.info('适用体重格式不正确，需为数字-数字');
        return;
      }

      // 验证适用年龄
      if (!this.catage.trim().match(/^\d+(\.\d+)?-\d+(\.\d+)?$/)) {
        ElMessage.info('适用年龄格式不正确，需为数字-数字');
        return;
      }

      const formData = new FormData();

      // 添加newProduct数据到FormData对象
      formData.append('goodname', this.goodName);
      formData.append('price', this.price);
      formData.append('number', this.stock);
      formData.append('description', this.description);
      formData.append('calorie', this.calorie);
      formData.append('catkind', this.catkind);
      formData.append('catweight', this.catweight);
      formData.append('catage', this.catage);
      formData.append('kind', this.selectedKind);
      formData.append('subkind', this.selectedSubkind);

      // 添加MultipartFile文件数据到FormData对象
      this.selectedFiles.forEach(file => {
        formData.append('mediaFiles', file);
      //  console.log(file);
      });

  //    console.log(this.selectedFiles.length);
   //   console.log(formData);

      // 发起fetch请求
      const response = await fetch('/good/upload-good', {
        method: 'POST',
        body: formData
      });

      this.selectedFiles = []; // 清空照片列表

      // 解析响应数据
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
    },
    isImage(file) {
      return file && file.type && file.type.startsWith('image/');
    },
    getURL(file){
      return URL.createObjectURL(file);
    },
    handleFileChange(event) {
      const files = Array.from(event.target.files);
      // console.log(files);
      // 检查文件类型
      const validTypes = ['image/png', 'image/jpeg', 'video/mp4'];
      const validFiles = files.filter(file => validTypes.includes(file.type));
      console.log(validFiles);
      // 如果有不支持的文件类型，提醒用户并返回
      if (validFiles.length < files.length) {
        ElMessage.info("不支持的文件格式。请上传png、jpg或mp4格式的文件。");
        return;
      }

      // 限制selectedFiles数组的大小不超过3
      const remainingSlots = 3 - this.selectedFiles.length;
      const newFiles = validFiles.slice(0, remainingSlots);
      const maxFiles = 3;
      const maxSize = 10485760; // 10MB limit

      // 检查文件数量
      if (files.length > maxFiles) {
        ElMessage.info(`您只能上传最多 ${maxFiles} 个文件。`);
        return;
      }

      // 检查文件大小
      const isAnyFileTooLarge = files.some(file => file.size > maxSize);
      if (isAnyFileTooLarge) {
        ElMessage.info("所有文件必须小于10MB，请重新选择文件。");
      return;
      }

      // 如果选择的文件超过3个，提醒用户
      if (this.selectedFiles.length + newFiles.length > 3) {
        ElMessage.info("最多只能上传3个文件。");
        return;
      }

    //  console.log(validFiles);

      // 更新selectedFiles数组，只包含到3个文件的限制
      this.selectedFiles = [...this.selectedFiles, ...newFiles];
   //   console.log(this.selectedFiles);

      // 更新input file控件以反映文件的更改
      if (this.selectedFiles.length === 3) {
        event.target.disabled = true;
      } else {
        event.target.disabled = false;
        event.target.value = ''; // 重置input file控件，以便用户可以重新选择文件
      }

      // 清除 input 的已选择文件
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = '';
      }

      // 如果选择的文件少于3个，则确保input不是disabled
      if (this.selectedFiles.length < 3) {
        event.target.disabled = false;
      }
    },
    goBack() {
      this.$router.go(-1);
    },
    removeFile(index) {
      // File objects are managed by the browser.
      this.selectedFiles.splice(index, 1);
      this.currentPreviewIndex = Math.max(0, Math.min(this.currentPreviewIndex, this.selectedFiles.length - 1)); // 从数组中移除选定文件

      // 如果现在少于3个文件，确保input是可用的
      if (this.selectedFiles.length < 3 && this.$refs.fileInput) {
        this.$refs.fileInput.disabled = false;
      }
      // 重置input的value
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = '';
      }
    },
    triggerFileInput() {
      // 使用 ref 访问文件输入并触发点击事件
      if (this.$refs.fileInput) {
        this.$refs.fileInput.click();
      } else {
        console.error('未找到文件输入框。');
      }
    },
    prevImage() {
      // 显示上一张图片的逻辑
      if (this.selectedFiles.length > 0) {
        this.currentPreviewIndex = (this.currentPreviewIndex - 1 + this.selectedFiles.length) % this.selectedFiles.length;
      }
    },
    nextImage() {
      // 显示下一张图片的逻辑
      if (this.selectedFiles.length > 0) {
        this.currentPreviewIndex = (this.currentPreviewIndex + 1) % this.selectedFiles.length;
      }
    }
    // 其他方法...
  },
  mounted() {
    // 初始化默认选项
    this.updateSubcategories();
  },
  computed: {
    currentFile() {
      return this.selectedFiles[this.currentPreviewIndex];
    },
    isFileInputDisabled() {
      return this.selectedFiles.length >= 3;
    },
  },
};
</script>
