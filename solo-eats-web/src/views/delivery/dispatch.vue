<script setup lang="ts">
import DispatchConfigPanel from './config/index.vue'
import DeliveryOrderPanel from './order/index.vue'
</script>

<template>
  <div class="page">
    <div class="page-lead">
      <div>
        <h2>派单中心</h2>
        <p>
          上半区 <span class="mono">GET / PUT /api/v1/admin/dispatch-config</span> 维护全局或门店级派单策略；
          下半区 <span class="mono">GET /api/v1/admin/deliveries</span> 查看配送单并调用
          <span class="mono">POST /deliveries/{id}/reassign</span> 改派。两个面板复用配送中心既有实现，
          不重复请求逻辑。
        </p>
      </div>
    </div>

    <section class="dispatch-section">
      <h3 class="section-heading">派单配置</h3>
      <DispatchConfigPanel />
    </section>

    <section class="dispatch-section">
      <h3 class="section-heading">配送单与改派</h3>
      <DeliveryOrderPanel />
    </section>
  </div>
</template>

<style scoped>
.dispatch-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-heading {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-muted);
}
</style>
