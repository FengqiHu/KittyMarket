<template>
  <div class="auth-page">
    <header class="auth-header">
      <router-link to="/" class="brand"><span class="brand-mark">猫</span><span>猫咪美食坊</span></router-link>
      <router-link to="/buyer">浏览商城</router-link>
    </header>
    <main class="auth-card">
      <section class="auth-story">
        <div class="auth-story-copy"><h1>猫粮与用品，<br />一站选购。</h1><p>主粮 · 零食 · 日常用品</p></div>
        <img class="auth-cat" src="@/assets/img/catbackground.png" alt="猫咪" />
        <router-link to="/buyer">查看在售商品 →</router-link>
      </section>
      <section class="auth-form-panel"><div class="auth-form-content"><slot /></div></section>
    </main>
    <footer class="auth-footer">猫咪美食坊</footer>
  </div>
</template>
