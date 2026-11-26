# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
ARG API_BASE_URL=http://localhost:8080/api
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Bake the backend URL into the production environment file.
RUN sed -i "s#apiBaseUrl: '.*'#apiBaseUrl: '${API_BASE_URL}'#" src/environments/environment.ts \
 && npx ng build --configuration production

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=4000
COPY --from=build /app/dist/cvpilot-ai ./dist/cvpilot-ai
COPY --from=build /app/node_modules ./node_modules
USER node
EXPOSE 4000
CMD ["node", "dist/cvpilot-ai/server/server.mjs"]
