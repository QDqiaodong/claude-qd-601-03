<template>
  <div class="eq-shell">
    <!-- 左侧竖排二级折叠菜单 -->
    <aside class="eq-side">
      <div class="eq-brand">
        <div class="eq-brand__mark">EQ</div>
        <div class="eq-brand__text">
          <strong>栗野马术</strong>
          <span>俱乐部运营台</span>
        </div>
      </div>

      <el-menu
        class="eq-menu"
        :default-active="route.path"
        :default-openeds="opened"
        background-color="transparent"
        text-color="#e7dcd7"
        active-text-color="#ffffff"
        @select="navigate"
      >
        <el-sub-menu v-for="group in menuGroups" :key="group.key" :index="group.key">
          <template #title>
            <span class="eq-menu__dot"></span>
            <span>{{ group.label }}</span>
          </template>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="eq-side__foot">
        <p>主色 栗棕 #6d4c41</p>
        <p>卡片墙 / 训练日历范式</p>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <main class="eq-main">
      <header class="eq-topbar">
        <div class="eq-topbar__text">
          <h1>{{ route.title }}</h1>
          <p>{{ route.hint }}</p>
        </div>
        <div class="eq-topbar__badge">
          <span class="eq-topbar__pip"></span>
          值班交接 · 马匹 · 栏位 · 排期 · 会员
        </div>
      </header>

      <section class="eq-page">
        <component :is="currentView" />
      </section>
    </main>
  </div>
</template>

<script setup>
import { menuGroups, route, currentView, navigate } from './router'

const opened = menuGroups.map((group) => group.key)
</script>

<style>
/* ---------------- 全局基调：栗棕主题（主色已在 main.css 里覆盖 Element Plus 变量） ---------------- */
body {
  background: #f4f1ef;
}

.eq-shell {
  display: flex;
  height: 100%;
  min-height: 100%;
}

/* ---------------- 左侧栏 ---------------- */
.eq-side {
  display: flex;
  flex-direction: column;
  width: 232px;
  flex: 0 0 232px;
  background: linear-gradient(180deg, #4a3129 0%, #6d4c41 100%);
  color: #f3ece9;
}

.eq-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 16px;
}

.eq-brand__mark {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: #f0e2d8;
  color: #6d4c41;
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 1px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.eq-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.eq-brand__text strong {
  font-size: 16px;
  letter-spacing: 1px;
}

.eq-brand__text span {
  font-size: 11px;
  color: #d3bdb4;
  letter-spacing: 2px;
}

.eq-menu {
  flex: 1;
  border-right: none;
  padding: 4px 8px;
  overflow-y: auto;
}

.eq-menu .el-sub-menu__title,
.eq-menu .el-menu-item {
  height: 42px;
  line-height: 42px;
  border-radius: 8px;
  margin: 2px 0;
  font-size: 14px;
}

.eq-menu .el-menu-item {
  padding-left: 40px !important;
  font-size: 13px;
  color: #e0d2cc;
}

.eq-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.09) !important;
  color: #fff;
}

.eq-menu .el-menu-item.is-active {
  background: #f0e2d8 !important;
  color: #4a3129 !important;
  font-weight: 600;
}

.eq-menu .el-sub-menu__title:hover {
  background: rgba(255, 255, 255, 0.07) !important;
}

.eq-menu__dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d9b8a8;
  margin-right: 9px;
  vertical-align: middle;
}

.eq-side__foot {
  padding: 12px 18px 18px;
  font-size: 11px;
  color: #c3aca3;
  line-height: 1.7;
  border-top: 1px solid rgba(255, 255, 255, 0.11);
}

.eq-side__foot p {
  margin: 0;
}

/* ---------------- 右侧主区 ---------------- */
.eq-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.eq-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 22px;
  background: #fff;
  border-bottom: 1px solid #eae2de;
}

.eq-topbar__text h1 {
  margin: 0;
  font-size: 19px;
  color: #4a3129;
  letter-spacing: 0.5px;
}

.eq-topbar__text p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9a8b85;
}

.eq-topbar__badge {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #6d4c41;
  background: #f3e9e4;
  border: 1px solid #e3d3cb;
  border-radius: 999px;
  padding: 6px 14px;
  white-space: nowrap;
}

.eq-topbar__pip {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6d4c41;
}

.eq-page {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 18px 22px 28px;
}

/* ---------------- 跨页面共用的小件 ---------------- */
.eq-panel {
  background: #fff;
  border: 1px solid #eae2de;
  border-radius: 12px;
  padding: 16px;
}

.eq-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.eq-panel__head h2 {
  margin: 0;
  font-size: 15px;
  color: #4a3129;
}

.eq-panel__head span {
  font-size: 12px;
  color: #9a8b85;
}

.eq-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.eq-muted {
  color: #9a8b85;
  font-size: 12px;
}

.eq-empty {
  padding: 32px 0;
  text-align: center;
  color: #a89a94;
  font-size: 13px;
}
</style>
