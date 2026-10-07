<template>
<div class="section-heading"><div><h1>我的收藏</h1><p>查看和管理已收藏的商品。</p></div><el-tag round effect="plain">{{ items.length }} 件商品</el-tag></div><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><div class="product-grid" v-loading="loading"><article class="product-card" v-for="item in items" :key="item.goodid"><button class="product-cover" @click="open(item)"><ProductMedia :path="item.picture" :alt="item.goodname" /><span class="product-category">{{ item.state === 0 ? '在售' : '暂不可购买' }}</span></button><div class="product-card-body"><h3>{{ item.goodname }}</h3><p>{{ item.description }}</p><div class="product-card-bottom"><span class="product-price"><small>¥</small>{{ Number(item.price).toFixed(2) }}</span><el-button text type="primary" @click="remove(item)">取消收藏</el-button></div></div></article></div><el-empty v-if="!loading && !items.length" description="暂无收藏商品"><el-button type="primary" @click="$router.push('/buyer')">浏览商品</el-button></el-empty>
</template>
<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';
export default { data: () => ({items:[],user:null,error:'',loading:false}), created() { this.load(); }, methods: {
 async load() { this.loading = true; try { this.user = (await axios.get('/now-usr')).data; if (!this.user) { this.error = '请登录后查看收藏'; return; } const {data} = await axios.post('/cart/showLike',null,{params:{userId:this.user.userid}}); this.items = (data || []).map(row => Object.fromEntries(Object.entries(row).map(([key,value]) => [key.toLowerCase(),value]))); } catch { this.error = '收藏加载失败，请稍后重试'; } finally { this.loading = false; } },
 async open(item) { try { await axios.post('/good/buyer-show-good/'+item.goodid); this.$router.push('/buyer-shop'); } catch { ElMessage.error('商品加载失败'); } },
 async remove(item) { try { await axios.post('/cart/addLike',null,{params:{goodid:item.goodid,iscancel:1,userid:this.user.userid}}); this.items = this.items.filter(row => row.goodid !== item.goodid); ElMessage.success('已取消收藏'); } catch { ElMessage.error('操作失败，请重试'); } },
} };
</script>
