<template>
<section class="legacy-page">
  <div class="container" v-if="isLoggedIn">
    <h1>上传多个商品</h1>
    <!-- 表单和输入字段 -->
    <div>
      <div class="form-group">
        <label for="goodName">商品名称：</label>
        <input type="text" v-model="goodName" placeholder="请输入商品名称" required>
        <span v-if="errors.goodName" class="error">{{ errors.goodName }}</span>
      </div>
      <div class="form-group">
        <label for="price">商品价格：</label>
        <input type="number" v-model="price" placeholder="请输入商品价格" required>
        <span v-if="errors.price" class="error">{{ errors.price }}</span>
      </div>

      <div class="form-group">
        <label for="stock">商品数量：</label>
        <input type="number" v-model="stock" placeholder="请输入商品库存" required>
        <span v-if="errors.stock" class="error">{{ errors.stock }}</span>
      </div>

      <div class="form-group">
        <label for="description">商品描述：</label>
        <input type="text" v-model="description" placeholder="请输入商品描述" required>
        <span v-if="errors.description" class="error">{{ errors.description }}</span>
      </div>
      <div class="form-group">
        <label for="description">卡路里：</label>
        <input type="text" v-model="calorie" placeholder="请输入商品卡路里cal/g" required>
        <span v-if="errors.calorie" class="error">{{ errors.calorie }}</span>
      </div>
      <div class="form-group">
        <label for="description">适用品种：</label>
        <input type="text" v-model="catkind" placeholder="请输入适用猫咪品种" required>
        <span v-if="errors.catkind" class="error">{{ errors.catkind }}</span>
      </div>
      <div class="form-group">
        <label for="description">适用体重：</label>
        <input type="text" v-model="catweight" placeholder="请输入适用猫咪体重" required>
        <span v-if="errors.catweight" class="error">{{ errors.catweight }}</span>
      </div>
      <div class="form-group">
        <label for="description">适用年龄：</label>
        <input type="text" v-model="catage" placeholder="请输入适用猫咪年龄" required>
        <span v-if="errors.catage" class="error">{{ errors.catage }}</span>
      </div>
      <div class="form-group">
          <label for="kind">商品大类：</label>
          <select v-model="selectedKind" @change="updateSubcategories">
            <option v-for="kind in kinds" :key="kind.value" :value="kind.value">{{ kind.text }}</option>
          </select>
      </div>
      <div class="form-group">
        <label for="subkind">商品子类：</label>
        <select v-model="selectedSubkind">
          <option v-for="subkind in subkinds" :key="subkind" :value="subkind">{{ subkind }}</option>
        </select>
      </div>
      <label for="kind">商品图片：</label>
      <div id="preview">
          <!-- 预览窗口 -->
          <div v-if="selectedFiles.length > 0">
            <div v-for="(file, index) in selectedFiles" :key="index" v-show="index === currentPreviewIndex">
              <img v-if="isImage(file)" :src="getURL(file)" alt="预览图">
              <video v-else controls :src="getURL(file)"></video>
              <button @click="removeFile(index)">X</button>
            </div>
          </div>
          <div v-else>
            没有选择文件
          </div>
      </div>
      <button type="button" @click="prevImage">＜</button>
      <button type="button" @click="nextImage">＞</button>

      <!-- 底部是一个上传按钮，在上传图片为空的时候时它显示的是“上传图片/视频”，在有内容的时候为“继续上传”-->
      <input type="file" :disabled="isFileInputDisabled" ref="fileInput" @change="handleFileChange" accept="image/png, image/jpeg, video/mp4" multiple >
      <!-- 添加自定义的上传按钮 -->
      <button type="button" @click="triggerFileInput">上传图片/视频</button>
      <!-- 图片错误提示 -->
      <span class="error" v-if="errors.pictureError">{{ errors.pictureError }}</span>
      <button @click="addProduct">添加商品</button>
    </div>

    <!-- 商品列表展示 -->
    <table>
      <thead>
        <tr>
            <th>商品名称</th>
            <th>价格</th>
            <th>数量</th>
            <th>描述</th>
          <th>卡路里</th>
          <th>适用品种</th>
          <th>适用体重</th>
          <th>适用年龄</th>
            <th>展示内容</th>
            <th>商品类别</th>
            <th>子类别</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(product, index) in productList" :key="index">
        <td>{{ product.goodname }}</td>
        <td>{{ product.price }}</td>
        <td>{{ product.number }}</td>
        <td>{{ product.description }}</td>
          <td>{{ product.calorie }}</td>
          <td>{{ product.catkind }}</td>
          <td>{{ product.catweight }}</td>
          <td>{{ product.catage }}</td>
        <td>
          <div v-for="(file, index) in product.mediaFiles" :key="index">
            <img v-if="isImage(file)" :src="getURL(file)" alt="Image view" >
            <video v-else controls :src="getURL(file)" ></video>
          </div>
        </td>
        <td>{{ product.kind }}</td>
        <td>{{ product.subkind }}</td>
        </tr>
      </tbody>
    </table>
    <!-- 确认发布所有商品按钮 -->
    <button @click="PredUploadProducts">确认发布所有商品</button>
  </div>
  <div v-else>
    您还未登录，请先<router-link to="/">登录</router-link>
  </div>
</section>
</template>
<script>
import { ElMessage } from 'element-plus';
import axios from "axios";

export default {
  data() {
    return {
      products: [], // 用于存储商品的数组
      goodName: '',
      price: '',
      stock: '',
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
      productList: [],
      errors: {
        pictureError: '',
        goodName: '',
        price: '',
        stock: '',
        description: '',
        calorie: '',
        catkind: '',
        catweight: '',
        catage: '',
      },
      currentUser:null,
      FilesList: new Map()
    };
  },
  created() {
    this.fetchUsrFromSession();
  },
  mounted() {
    // 初始化默认选项
    this.updateSubcategories();
  },
  computed: {
    isLoggedIn() {
      // 根据当前用户数据判断用户是否登录
      return !!this.currentUser;
    },
    currentFile() {
      return this.selectedFiles[this.currentPreviewIndex];
    },
    isFileInputDisabled() {
      return this.selectedFiles.length >= 3;
    },
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

        return true;
      } catch (error) {
        console.error('获取用户数据错误:', error);
        return false;
      }
    },
    addProduct() {
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


      // 创建新商品对象
      const newProduct = {
        goodname: this.goodName,
        price: this.price,
        number: this.stock,
        description: this.description,
        calorie: this.calorie,
        catkind: this.catkind,
        catweight: this.catweight,
        catage: this.catage,
        kind: this.selectedKind,
        subkind: this.selectedSubkind,
        mediaFiles: this.selectedFiles
      };

      // 添加商品到列表
      this.productList.push(newProduct);

      // 将商品的媒体文件添加到 FormData 对象中
      this.selectedFiles.forEach(file => {
        // 使用商品名称作为键，将文件添加到 FilesList 中
        this.FilesList.set(this.goodName, file)
      });

      // 清空输入
      this.goodName = '';
      this.price = '';
      this.stock = '';
      this.description = '';
      this.calorie = '';
      this.catkind = '';
      this.catweight = '';
      this.catage = '';
      this.selectedFiles = [];
      console.log(this.selectedFiles);
      ElMessage.info("商品已添加"); // 提示用户
    },
    isImage(file) {
      return file && file.type && file.type.startsWith('image/');
    },
    getURL(file){
      return URL.createObjectURL(file);
    },
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
    handleFileChange(event) {
      const files = Array.from(event.target.files);

      // 检查文件类型
      const validTypes = ['image/png', 'image/jpeg', 'video/mp4'];
      const validFiles = files.filter(file => validTypes.includes(file.type));

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

      // 更新selectedFiles数组，只包含到3个文件的限制
      this.selectedFiles = [...this.selectedFiles, ...validFiles];

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
    removeFile(index) {
      URL.revokeObjectURL(this.selectedFiles[index]); // 释放内存中的URL
      this.selectedFiles.splice(index, 1); // 从数组中移除选定文件

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
    async PredUploadProducts() {
      // 构建商品名称数组
      const goodnames = this.productList.map(product => product.goodname);

      // 发起请求检查重名商品
      const response = await fetch('/good/upload-multiple-goods', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ goodnames: goodnames })
      });

      // 解析响应数据
      const responseData = await response.json();

      if (responseData.page === '/error') {
        // 重定向到错误页面，并将错误消息和重定向目标作为参数传递
        this.$router.push({ path: '/error', query: { err: responseData.msg, to: responseData.data }})
      } else if (responseData.page === '/success') {
        await this.submitProducts();
      } else if (responseData.page === null) {
        console.log("未知页面类型");
      } else {
        this.$router.push({ path: responseData.page });
      }
    },
    async submitProducts() {
      for (const product of this.productList) {
        const formData = new FormData();
        formData.append('goodname', product.goodname);
        formData.append('price', product.price);
        formData.append('number', product.number);
        formData.append('description', product.description);
        formData.append('calorie', this.calorie);
        formData.append('catkind', this.catkind);
        formData.append('catweight', this.catweight);
        formData.append('catage', this.catage)
        formData.append('kind', product.kind);
        formData.append('subkind', product.subkind);

        // 遍历FilesList，只处理与当前商品名称匹配的文件
        for (const [goodname, file] of this.FilesList.entries()) {
          if (goodname === product.goodname) {
            formData.append('mediaFiles', file);
          }
        }

        const response = await fetch('/good/upload-good', {
          method: 'POST',
          body: formData
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
    // 其他方法
  },

};
</script>
