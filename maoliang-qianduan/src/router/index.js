import {createRouter, createWebHashHistory} from 'vue-router';
const LoginComponent = () => import('@/components/LoginComponent.vue');
const RegisterComponent = () => import('@/components/RegisterComponent.vue');
const ChooseRegister = () => import('@/components/ChooseRegister.vue');
const BuyerMain = () => import('@/components/BuyerMain.vue');
const SellerMain = () => import('@/components/SellerMain.vue');
const UpdatePasswordComponent = () => import('@/components/UpdatePasswordComponent.vue');
const ShowGoods = () => import('@/components/ShowGoods');
const ShowUserInfo = () => import('@/components/ShowUserInfo.vue');
const ShowHistoryGoods = () => import('@/components/ShowHistoryGoods.vue');
const ShowAllUsers = () => import('@/components/ShowAllUsers.vue');
const UploadMultipleGoods = () => import('@/components/UploadMultipleGoods.vue');
const ForgotPasswordComponent = () => import('@/components/ForgotPasswordComponent.vue');
const GuestComponent = () => import('@/components/GuestComponent.vue');
const UserOrderHistory = () => import('@/components/UserOrderHistory.vue');
const SuccessComponent = () => import('@/components/SuccessComponent.vue');
const SecretQuestionComponent = () => import('@/components/SecretQuestionComponent.vue');
const BuyerHistory = () => import('@/components/BuyerHistory.vue');
const BuyerLikes = () => import('@/components/BuyerLikes.vue');
const BuyerCart = () => import('@/components/BuyerCart.vue');
const BuyerFillInfo = () => import('@/components/BuyerFillInfo.vue');
const BuyerShop = () => import('@/components/BuyerShop');
const ErrorComponent = () => import('@/components/ErrorComponent.vue');
const ShowSearchGoods = () => import('@/components/ShowSearchGoods.vue');
const ShowSearchHistoryGoods = () => import('@/components/ShowSearchHistoryGoods.vue');
const BuyerSearch = () => import('@/components/BuyerSearch.vue');
const UploadOneGood = () => import('@/components/UploadOneGood.vue');
const BuyerShowCat = () => import('@/components/BuyerShowCat.vue');
const BuyerUploadCat = () => import('@/components/BuyerUploadCat.vue');
const BuyerShowRecommend = () => import('@/components/BuyerShowRecommend.vue');
const BuyerAfterSale = () => import('@/components/BuyerAfterSale.vue');
const BuyerPay = () => import('@/components/BuyerPay.vue');

const routes = [
  { path: '/', component: LoginComponent },
  { path: '/login', component: LoginComponent },
  { path: '/error', component: ErrorComponent },
  { path: '/register', component: RegisterComponent },
  { path: '/choose-register', component: ChooseRegister },
  {
    path: '/buyer',
    name: 'BuyerMain',
    component: BuyerMain,

  },
  {
    path: '/seller',
    name: 'SellerMain',
    component: SellerMain,
    redirect: { name: 'ShowGoods' },
    children: [
      {
        path: 'ShowGoods',
        name: 'ShowGoods',
        component: ShowGoods
      },
      {
        path: 'ShowSearchGoods',
        name: 'ShowSearchGoods',
        component: ShowSearchGoods
      },
      {
        path: 'update-password',
        name: 'UpdatePassword',
        component: UpdatePasswordComponent
      },
      {
        path: 'upload-onegood',
        name: 'UploadOneGood',
        component: UploadOneGood
      },
      {
        path: 'show-userinfo',
        name: 'ShowUserInfo',
        component: ShowUserInfo
      },
      {
        path: 'user-order-history',
        name: 'UserOrderHistory',
        component: UserOrderHistory
      },
      {
        path: 'show-historygoods',
        name: 'ShowHistoryGoods',
        component: ShowHistoryGoods
      },
      {
        path: 'show-searchhistorygoods',
        name: 'ShowSearchHistoryGoods',
        component: ShowSearchHistoryGoods
      },
      {
        path: 'show-allusers',
        name: 'ShowAllUsers',
        component: ShowAllUsers
      },
      {
        path: 'upload-multiplegoods',
        name: 'UploadMultipleGoods',
        component: UploadMultipleGoods
      },
      // ... 其他子路由
    ]
    // 可以添加其他路由配置，如 meta 数据等
  },
  {
    path: '/forgot-password',
    name: 'ForgotPasswordComponent',
    component: ForgotPasswordComponent // 替换为您的实际组件
  },
  {
    path: '/guest',
    name: 'GuestComponent',
    component: GuestComponent // 替换为您的实际组件
  },
  {
    path: '/success',
    name: 'Success',
    component: SuccessComponent,
    props: true // 启用 props 将路由参数传递到组件
  },
  {
    path: '/secret-question',
    name: 'SecretQuestion',
    component: SecretQuestionComponent
  },
  {
    path: '/buyer-history',
    name: 'buyerHistory',
    component: BuyerHistory
  },
  {
    path: '/buyer-search',
    name: 'buyerSearch',
    component: BuyerSearch
  },
  {
    path: '/likes',
    name: 'BuyerLikes',
    component: BuyerLikes,
  },
  {
    path: '/cart',
    name: 'BuyerCart',
    component: BuyerCart,
  },
  {
    path: '/buyer-fill-info',
    name: 'BuyerFillInfo',
    component: BuyerFillInfo
  },
  {
    path: '/buyer-shop',
    name: 'BuyerShop',
    component: BuyerShop
  },
  {
    path: '/buyer-show-cat',
    name: 'BuyerShowCat',
    component: BuyerShowCat,

  },
  {
    path: '/buyer-upload-cat',
    name: 'BuyerUploadCat',
    component: BuyerUploadCat,

  },
  {
    path: '/buyer-show-recommend',
    name: 'BuyerShowRecommend',
    component: BuyerShowRecommend,

  },
  {
    path: '/buyer-after-sale',
    name: 'BuyerAfterSale',
    component: BuyerAfterSale,

  },
  {
    path: '/buyer-pay',
    name: 'BuyerPay',
    component: BuyerPay,

  },
  // ... 其他路由
];

const router = createRouter({
  history:  createWebHashHistory(),
  routes,
});

export default router;
