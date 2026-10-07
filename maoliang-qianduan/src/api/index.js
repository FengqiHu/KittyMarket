// api/index.js

// 模拟函数，未来替换为实际的 API 请求
export default {
    getGoods() {
      // 模拟数据
      return Promise.resolve({
        data: [{ id: 1, name: '商品1' }, { id: 2, name: '商品2' }]
      });
    },
    getUserInfo() {
      // 模拟数据
      return Promise.resolve({
        data: { id: 1, name: '用户1' }
      });
    },
    // 可以根据需要添加更多模拟方法
  };

