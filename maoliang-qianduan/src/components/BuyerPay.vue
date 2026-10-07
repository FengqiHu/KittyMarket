<template>
<section class="legacy-page">
  <!-- 页面头部 -->

  <!-- 左侧导航 -->
    <div class="content">

        <!-- 右侧内容区域 -->
        <div class="right" >
            <div class="container" >
                <div >
                    <h2>请选择支付方式</h2>
                </div>
                <div >
                    <label for="orderNumber">订单编号:</label>
                    <input type="text" id="orderNumber" placeholder="请输入订单编号" >
                </div>
                <div >
                    <label for="orderPrice">订单价格:</label>
                    <input type="text" id="orderPrice" placeholder="请输入订单价格" >
                </div>
                <div >
                    <button >银行支付</button>
                    <button >信用卡支付</button>
                    <button>第三方支付</button>
                </div>
            </div>
        </div>




    </div>

</section>
</template>
<script>
export default {
    name: "BuyerPay"
}
</script>
