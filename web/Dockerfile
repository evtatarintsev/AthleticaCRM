FROM node:24-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run check && npm run build

FROM nginx:stable-alpine
COPY --from=builder /build/dist/ /usr/share/nginx/html/web/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
