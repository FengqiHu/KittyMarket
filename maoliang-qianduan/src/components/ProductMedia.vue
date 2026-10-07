<template><div class="product-media"><video v-if="isVideo" :src="src" controls @click.stop></video><el-image v-else :src="src" :alt="alt" fit="contain" lazy><template #error><div class="media-fallback"><el-icon><IconPicture /></el-icon><span>商品图片暂不可用</span></div></template></el-image></div></template>
<script>
import { mediaUrl } from '../utils/media';
export default { props: { path: String, alt: { type: String, default: '商品图片' } }, computed: { src() { return mediaUrl(this.path); }, isVideo() { return /\.(mp4|avi|webm)$/i.test(this.path || ''); } } };
</script>
