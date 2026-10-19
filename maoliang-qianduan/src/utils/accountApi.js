import axios from 'axios';

export async function accountRequest(method, path = '', body) {
  try {
    const { data } = await axios({ method, url: '/account' + path, data: body });
    if (data.page === '/error') throw new Error(data.msg || '操作失败，请重试');
    return data.data;
  } catch (error) {
    throw new Error(error.response?.data?.msg || (error.response?.status === 401 ? '请先登录后再操作' : error.message || '操作失败，请重试'));
  }
}
