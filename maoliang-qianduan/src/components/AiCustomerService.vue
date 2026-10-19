<template>
  <div class="ai-cs-root">
    <!-- 右下角悬浮按钮 -->
    <button
      type="button"
      class="ai-cs-fab"
      :class="{ 'is-active': isOpen }"
      aria-label="打开AI智能客服"
      @click="toggleOpen"
    >
      <span class="ai-cs-fab-icon" aria-hidden="true">
        <svg v-if="!isOpen" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
        </svg>
        <el-icon v-else><IconClose /></el-icon>
      </span>
      <span class="ai-cs-fab-label">{{ isOpen ? '收起客服' : 'AI 客服' }}</span>
    </button>

    <!-- 客服对话窗口 -->
    <section
      v-if="isOpen"
      class="ai-cs-panel surface"
      role="dialog"
      aria-label="猫咪美食坊 AI 智能客服"
    >
      <header class="ai-cs-header">
        <div class="ai-cs-brand">
          <span class="ai-cs-avatar">猫</span>
          <div>
            <div class="ai-cs-title-row">
              <strong>AI 智能客服</strong>
              <span class="ai-cs-badge">Dify Workflow</span>
            </div>
            <p class="ai-cs-subtitle">偏好导购 · 对比套餐 · 科学喂养 · 购物车诊断 · 订单售后</p>
          </div>
        </div>
        <div class="ai-cs-header-actions">
          <el-button
            size="small"
            :type="viewMode === 'history' ? 'primary' : 'default'"
            plain
            @click="switchView(viewMode === 'history' ? 'chat' : 'history')"
          >
            {{ viewMode === 'history' ? '返回对话' : `历史记录${conversations.length ? ' (' + conversations.length + ')' : ''}` }}
          </el-button>
          <el-button size="small" plain title="开始新对话" @click="startNewConversation">
            <el-icon><IconPlus /></el-icon>新对话
          </el-button>
          <button type="button" class="ai-cs-close-btn" aria-label="关闭客服窗口" @click="isOpen = false">
            <el-icon><IconClose /></el-icon>
          </button>
        </div>
      </header>

      <!-- 历史对话列表视图 -->
      <div v-if="viewMode === 'history'" class="ai-cs-history-body">
        <div class="ai-cs-history-toolbar">
          <span>共 {{ conversations.length }} 条历史会话</span>
          <el-button type="primary" size="small" @click="startNewConversation">
            <el-icon><IconPlus /></el-icon>发起新咨询
          </el-button>
        </div>
        <div v-if="loadingHistory" class="ai-cs-empty">正在加载历史对话...</div>
        <el-empty v-else-if="!conversations.length" description="暂无历史对话，点击上方按钮开始咨询吧" :image-size="72" />
        <ul v-else class="ai-cs-conv-list">
          <li
            v-for="conv in conversations"
            :key="conv.id"
            class="ai-cs-conv-item"
            :class="{ 'is-current': currentConversationId === conv.id }"
          >
            <button type="button" class="ai-cs-conv-main" @click="selectConversation(conv)">
              <div class="ai-cs-conv-top">
                <strong class="ai-cs-conv-title">{{ conv.title }}</strong>
                <span class="ai-cs-conv-time">{{ conv.updatedAt }}</span>
              </div>
              <p class="ai-cs-conv-preview">{{ conv.lastMessage || '点击查看对话详情' }}</p>
              <span class="ai-cs-conv-meta">{{ conv.messageCount }} 条消息</span>
            </button>
            <button
              type="button"
              class="ai-cs-conv-del"
              title="删除该对话"
              aria-label="删除该对话"
              @click.stop="removeConversation(conv.id)"
            >
              <el-icon><IconDelete /></el-icon>
            </button>
          </li>
        </ul>
      </div>

      <!-- 当前对话视图 -->
      <div v-else class="ai-cs-chat-body">
        <div ref="messageContainer" class="ai-cs-messages">
          <!-- 欢迎引导卡片 -->
          <div class="ai-cs-welcome">
            <div class="ai-cs-welcome-title">🐾 您好{{ user ? '，' + user.username : '' }}！我是猫咪美食坊 AI 客服</div>
            <p class="ai-cs-welcome-desc">
              由 <strong>Dify 多分支专家工作流</strong>驱动，支持<strong>偏好选粮推荐、多商品对比与三件套搭配、科学喂食量测算、购物车凑单诊断、订单查询及对话式取消/确认收货</strong>。
            </p>
            <div class="ai-cs-quick-prompts">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                type="button"
                class="ai-cs-prompt-chip"
                :disabled="sending"
                @click="sendQuickPrompt(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="ai-cs-msg-row"
            :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <div class="ai-cs-bubble">
              <div class="ai-cs-msg-text" v-html="formatMessage(msg.content)"></div>
              <div v-if="msg.createdAt || (msg.role === 'assistant' && msg.intent)" class="ai-cs-msg-time">
                <span v-if="msg.role === 'assistant' && msg.intent">{{ intentLabel(msg.intent) }} · </span>{{ msg.createdAt }}
              </div>
            </div>

            <!-- 导购推荐的 3 款商品卡片 -->
            <div v-if="msg.recommendations && msg.recommendations.length" class="ai-cs-rec-section">
              <div class="ai-cs-rec-header">
                <span>✨ {{ recommendationSectionTitle(msg.intent, msg.recommendations.length) }}</span>
              </div>
              <div class="ai-cs-rec-list">
                <article
                  v-for="item in msg.recommendations"
                  :key="item.goodid"
                  class="ai-cs-rec-card"
                >
                  <img
                    class="ai-cs-rec-img"
                    :src="getImage(item.picture)"
                    :alt="item.goodname"
                    @click="openProductDetail(item.goodid)"
                  />
                  <div class="ai-cs-rec-info">
                    <div class="ai-cs-rec-title-line">
                      <strong class="ai-cs-rec-name" @click="openProductDetail(item.goodid)">{{ item.goodname }}</strong>
                      <span class="ai-cs-rec-price">¥{{ Number(item.price).toFixed(2) }}</span>
                    </div>
                    <div class="ai-cs-rec-tags">
                      <span class="ai-cs-tag-kind">{{ item.kind }} · {{ item.subkind }}</span>
                      <span v-if="item.matchReason" class="ai-cs-tag-reason">{{ item.matchReason }}</span>
                    </div>
                    <p class="ai-cs-rec-desc">{{ item.description }}</p>
                    <div class="ai-cs-rec-actions">
                      <span class="ai-cs-rec-stock">库存 {{ item.number }} 件</span>
                      <div class="ai-cs-rec-btns">
                        <el-button size="small" @click="openProductDetail(item.goodid)">查看详情</el-button>
                        <el-button type="primary" size="small" @click="addProductToCart(item)">加入购物车</el-button>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <!-- 订单状态与快捷处理卡片 -->
            <div v-if="msg.orders && msg.orders.length" class="ai-cs-order-section">
              <div class="ai-cs-rec-header">
                <span>📦 相关订单记录</span>
              </div>
              <div class="ai-cs-order-list">
                <div v-for="order in msg.orders" :key="order.orderid" class="ai-cs-order-card">
                  <div class="ai-cs-order-top">
                    <strong>订单 #{{ order.orderid }}</strong>
                    <el-tag
                      size="small"
                      :type="order.orderstate === 5 ? 'success' : order.orderstate === -1 ? 'info' : 'warning'"
                    >
                      {{ order.statusText }}
                    </el-tag>
                  </div>
                  <div class="ai-cs-order-detail">
                    <span>{{ order.goodname }} × {{ order.number }} 件</span>
                    <span class="ai-cs-order-price">¥{{ Number(order.price * order.number).toFixed(2) }}</span>
                  </div>
                  <div class="ai-cs-order-addr">
                    收货：{{ order.recipientname }} · {{ order.telephone }} · {{ order.address }}
                  </div>
                  <div class="ai-cs-order-actions">
                    <el-button
                      v-if="order.canCancel"
                      size="small"
                      type="danger"
                      plain
                      :loading="actingOrderId === order.orderid"
                      @click="handleOrderAction(order.orderid, 'cancel')"
                    >
                      取消订单
                    </el-button>
                    <el-button
                      v-if="order.canConfirm"
                      size="small"
                      type="success"
                      plain
                      :loading="actingOrderId === order.orderid"
                      @click="handleOrderAction(order.orderid, 'confirm')"
                    >
                      确认收货
                    </el-button>
                    <el-button size="small" text @click="goToOrderHistory">前往我的订单</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="sending" class="ai-cs-msg-row is-assistant">
            <div class="ai-cs-bubble ai-cs-typing">AI 客服正在为您查询与匹配...</div>
          </div>
        </div>

        <!-- 底部输入框 -->
        <form class="ai-cs-input-bar" @submit.prevent="sendMessage">
          <el-input
            v-model="inputText"
            placeholder="输入导购需求（推荐3款商品）或订单问题..."
            :disabled="sending"
            clearable
          />
          <el-button type="primary" native-type="submit" :loading="sending" :disabled="!inputText.trim()">
            发送
          </el-button>
        </form>
      </div>
    </section>
  </div>
</template>

<script>
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { mediaUrl } from '../utils/media';

const GUEST_TOKEN_KEY = 'kittymarket_aics_guest_token';

export default {
  name: 'AiCustomerService',
  props: {
    user: {
      type: Object,
      default: null,
    },
  },
  data() {
    return {
      isOpen: false,
      viewMode: 'chat', // 'chat' | 'history'
      conversations: [],
      currentConversationId: null,
      messages: [],
      inputText: '',
      sending: false,
      loadingHistory: false,
      actingOrderId: null,
      guestToken: '',
      quickPrompts: [
        '根据我的偏好推荐3款商品',
        '帮我搭配一套主粮+零食+日用三件套',
        '对比幼猫奶糕粮和烘焙粮哪个好',
        '我家猫咪一天应该吃多少克猫粮？',
        '诊断我的购物车并推荐互补好物',
        '查询我的最近订单状态',
        '未发货订单怎么取消或退换货？',
      ],
    };
  },
  watch: {
    user() {
      if (this.isOpen) {
        this.loadConversations(true);
      }
    },
  },
  created() {
    this.initGuestToken();
  },
  methods: {
    intentLabel(intent) {
      const map = {
        shopping_guide: '专属导购推荐',
        product_comparison: '对比测评与套餐',
        feeding_advisor: '科学喂养顾问',
        cart_advisor: '购物车凑单诊断',
        order_support: '订单与售后服务',
        general: '商城全能管家',
      };
      return map[intent] || 'AI 智能回复';
    },
    recommendationSectionTitle(intent, count) {
      if (intent === 'product_comparison') return `为您整理 ${count} 款对比/套餐搭配方案`;
      if (intent === 'feeding_advisor') return `为您搭配 ${count} 款科学喂养与营养调理好物`;
      if (intent === 'cart_advisor') return `为您推荐 ${count} 款购物车互补凑单商品`;
      return `为您精选 ${count} 款偏好匹配好物`;
    },
    initGuestToken() {
      let token = localStorage.getItem(GUEST_TOKEN_KEY);
      if (!token) {
        token = 'g_' + Math.random().toString(36).slice(2, 12) + '_' + Date.now().toString(36);
        localStorage.setItem(GUEST_TOKEN_KEY, token);
      }
      this.guestToken = token;
    },
    getImage(picture) {
      return mediaUrl(picture);
    },
    toggleOpen() {
      this.isOpen = !this.isOpen;
      if (this.isOpen) {
        this.loadConversations(false);
        this.scrollToBottom();
      }
    },
    switchView(mode) {
      this.viewMode = mode;
      if (mode === 'history') {
        this.loadConversations(false);
      } else {
        this.scrollToBottom();
      }
    },
    async loadConversations(resetCurrent = false) {
      this.loadingHistory = true;
      try {
        const { data } = await axios.get('/ai-cs/conversations', {
          params: { guestToken: this.guestToken },
        });
        if (data && Array.isArray(data.data)) {
          this.conversations = data.data;
          if (resetCurrent) {
            this.currentConversationId = null;
            this.messages = [];
          } else if (!this.currentConversationId && this.conversations.length > 0 && !this.messages.length) {
            // 默认自动载入最近一次对话，方便用户接着聊或查看历史
            await this.selectConversation(this.conversations[0], false);
          }
        }
      } catch (e) {
        console.error('加载客服历史记录失败:', e);
      } finally {
        this.loadingHistory = false;
      }
    },
    startNewConversation() {
      this.currentConversationId = null;
      this.messages = [];
      this.viewMode = 'chat';
      this.scrollToBottom();
    },
    async selectConversation(conv, switchToChat = true) {
      try {
        const { data } = await axios.get(`/ai-cs/conversations/${conv.id}/messages`, {
          params: { guestToken: this.guestToken },
        });
        if (data && Array.isArray(data.data)) {
          this.currentConversationId = conv.id;
          this.messages = data.data;
          if (switchToChat) {
            this.viewMode = 'chat';
          }
          this.scrollToBottom();
        }
      } catch {
        ElMessage.error('无法加载该对话记录');
      }
    },
    async removeConversation(convId) {
      try {
        const { data } = await axios.delete(`/ai-cs/conversations/${convId}`, {
          params: { guestToken: this.guestToken },
        });
        if (data && data.page === 'success') {
          this.conversations = this.conversations.filter(c => c.id !== convId);
          if (this.currentConversationId === convId) {
            this.currentConversationId = null;
            this.messages = [];
          }
          ElMessage.success('对话记录已删除');
        }
      } catch {
        ElMessage.error('删除对话失败，请重试');
      }
    },
    sendQuickPrompt(prompt) {
      this.inputText = prompt;
      this.sendMessage();
    },
    async sendMessage() {
      const text = this.inputText.trim();
      if (!text || this.sending) return;
      this.inputText = '';
      this.sending = true;

      // 乐观展示用户消息
      const tempId = 'temp_' + Date.now();
      this.messages.push({
        id: tempId,
        role: 'user',
        content: text,
        createdAt: '刚刚',
      });
      this.scrollToBottom();

      try {
        const { data } = await axios.post('/ai-cs/chat', {
          conversationId: this.currentConversationId,
          message: text,
          guestToken: this.guestToken,
        });
        if (data && data.page === 'success' && data.data) {
          const { conversation, userMessage, assistantMessage } = data.data;
          this.currentConversationId = conversation.id;
          const idx = this.messages.findIndex(m => m.id === tempId);
          if (idx !== -1) {
            this.messages.splice(idx, 1, userMessage, assistantMessage);
          } else {
            this.messages.push(assistantMessage);
          }
          this.upsertConversationList(conversation);
        } else {
          ElMessage.error((data && data.msg) || '发送失败，请重试');
        }
      } catch {
        ElMessage.error('网络异常，请稍后再试');
      } finally {
        this.sending = false;
        this.scrollToBottom();
      }
    },
    async handleOrderAction(orderId, action) {
      if (this.actingOrderId) return;
      this.actingOrderId = orderId;
      try {
        const { data } = await axios.post('/ai-cs/order-action', {
          conversationId: this.currentConversationId,
          orderId,
          action,
          guestToken: this.guestToken,
        });
        if (data && data.page === 'success' && data.data) {
          const { conversation, userMessage, assistantMessage } = data.data;
          this.currentConversationId = conversation.id;
          this.messages.push(userMessage, assistantMessage);
          this.upsertConversationList(conversation);
          ElMessage.success(action === 'cancel' ? '订单状态已更新' : '确认收货已处理');
          this.scrollToBottom();
        } else {
          ElMessage.warning((data && data.msg) || '订单操作未完成');
        }
      } catch {
        ElMessage.error('订单操作失败，请稍后重试');
      } finally {
        this.actingOrderId = null;
      }
    },
    upsertConversationList(conv) {
      if (!conv) return;
      const existingIdx = this.conversations.findIndex(c => c.id === conv.id);
      if (existingIdx !== -1) {
        this.conversations.splice(existingIdx, 1);
      }
      this.conversations.unshift(conv);
    },
    async openProductDetail(goodid) {
      try {
        const { data } = await axios.post(`/good/buyer-show-good/${goodid}`);
        if (data && data.page && data.page !== '/error') {
          this.$router.push({ path: data.page, query: { goodid: String(goodid) } });
        } else {
          ElMessage.error('无法打开该商品详情');
        }
      } catch {
        ElMessage.error('打开商品详情失败');
      }
    },
    async addProductToCart(item) {
      if (!this.user || !this.user.userid) {
        ElMessage.info('请先登录后再将商品加入购物车');
        this.$router.push('/login');
        return;
      }
      try {
        await axios.post('/cart/create-cart', null, {
          params: { goodid: item.goodid, userid: this.user.userid },
        });
        ElMessage.success(`已将「${item.goodname}」加入购物车`);
      } catch {
        ElMessage.error('加入购物车失败，请稍后重试');
      }
    },
    goToOrderHistory() {
      this.$router.push('/buyer-history');
    },
    formatMessage(text) {
      if (!text) return '';
      const escaped = String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
      return escaped
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\n/g, '<br/>');
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.messageContainer;
        if (el) {
          el.scrollTop = el.scrollHeight;
        }
      });
    },
  },
};
</script>

