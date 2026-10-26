# solo-eats-web-merchant

商家中心独立前端工程，与 `solo-eats-web`（平台运营后台）分离部署。

## 运行

```bash
npm install
npm run dev      # http://localhost:5174/merchant/
npm run build    # vue-tsc --noEmit && vite build
```

开发代理与后端地址见 `vite.config.ts`：`/api` 转发到 `VITE_API_PROXY_TARGET`（默认 `http://localhost:8080`）。复制 `.env.example` 为 `.env.local` 后按环境覆盖。

## 结构

```
src/
├── api/        # 转发 @shared/api 的薄封装（便于页面按 @/api/... 引用）
├── constants/  # 同上，转发 @shared/constants
├── utils/      # 同上，转发 @shared/utils
├── layouts/    # MerchantLayout：侧边栏 + 门店切换
├── router/     # 静态路由，/merchant/ 为 base
├── stores/     # auth（复用共享 store）、shop（当前门店）
└── views/      # 登录、订单、商品、结算、404
```

共享代码在 `../solo-eats-web/shared`，通过 Vite 别名 `@shared` 与 tsconfig `paths` 引入，不复制第二份 `http.ts` / `money.ts`。

`src/pages.json` 是路由清单的声明式快照，供文档与后续菜单生成参考；运行时以 `src/router/index.ts` 为准。

## 身份与门店

- 账号密码登录的响应不含 `merchantId`/`shopId`，共享 store 从 JWT 声明解码补齐。
- 令牌绑定 `shopId` 的账号只能看该门店，顶栏下拉不可切换；令牌无 `shopId` 的商家管理员可切换，选择结果写入 `localStorage` 的 `solo-eats.merchant.shopId`。
- `@shared/api/http.ts` 对商家角色请求注入 `X-Shop-Id` 头。后端当前以 JWT 内 `shopId` 为准，该头为多门店能力预留，后端未读取前不改变可见数据范围。
