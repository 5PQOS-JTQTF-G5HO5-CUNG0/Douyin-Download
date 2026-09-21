FROM mcr.microsoft.com/playwright:v1.42.1-jammy

WORKDIR /app

# 安装依赖
COPY package*.json ./
RUN npm install --omit=dev

# 确保安装精准匹配当前 Playwright 版本的 Chromium 及其 headless-shell 二进制
RUN npx playwright install chromium

# 拷贝服务端源码
COPY . .

ENV PORT=3000
EXPOSE 3000

CMD ["node", "src/server.js"]
