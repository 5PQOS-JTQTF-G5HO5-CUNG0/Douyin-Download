FROM mcr.microsoft.com/playwright:v1.42.1-jammy

WORKDIR /app

# 优先安装依赖利用 Docker 缓存
COPY package*.json ./
RUN npm install --omit=dev

# 拷贝服务端源码
COPY . .

ENV PORT=3000
EXPOSE 3000

CMD ["node", "src/server.js"]
