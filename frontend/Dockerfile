# Dev image (Vite dev server com HMR).
# Para producao, fazer multi-stage com nginx servindo dist/.
FROM node:20-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY . .

EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
