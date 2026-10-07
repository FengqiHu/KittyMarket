<template>
<section class="legacy-page">
  <!-- 页面头部 -->

  <!-- 左侧导航 -->
    <div class="content">

    </div>
  <!-- 右侧内容区域 -->
    <div class="right" >
        <div class="container">
            <router-link :to="{ name: 'BuyerMain' }">
                返回
            </router-link>
            <div class="centered-container">
                <h2>我的售后</h2>
            </div>
            <el-table :data="afterSales" empty-text="暂无记录"><el-table-column label="商品名称" min-width="120"><template #default="{row: afterSale}">{{ afterSale.productName }}</template></el-table-column>
<el-table-column label="物流状态" min-width="120"><template #default="{row: afterSale}">{{ afterSale.logisticsStatus }}</template></el-table-column>
<el-table-column label="申请售后" min-width="120"><template #default="{row: afterSale}">
                        <button :class="{ 'green-btn': afterSale.afterSaleApplied, 'red-btn': !afterSale.afterSaleApplied }"
                                @click="applyForAfterSale(afterSale.id)">
                            {{ afterSale.afterSaleApplied ? '已申请' : '申请' }}
                        </button>
                    </template></el-table-column>
<el-table-column label="售后结果" min-width="120"><template #default="{row: afterSale}">
                        <button :class="{ 'green-btn': afterSale.afterSaleResult === '和解', 'red-btn': afterSale.afterSaleResult === '退货退款' }"
                                @click="handleAfterSaleResult(afterSale.id)">
                            {{ afterSale.afterSaleResult }}
                        </button>
                    </template></el-table-column></el-table>
        </div>
    </div>
</section>
</template>
<script>
export default {
    name: "BuyerAfterSale"
}
</script>
