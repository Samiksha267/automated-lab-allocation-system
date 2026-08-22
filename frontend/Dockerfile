# --- Build stage -----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm install

COPY . .

# Baked in at build time - Vite inlines VITE_* vars into the production bundle.
ARG VITE_API_BASE_URL=http://localhost:8080/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

# --- Runtime stage: serve the static build with a lightweight web server ----
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
