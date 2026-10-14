# =====================================================================================
# Frontend image: a MULTI-STAGE build.
#   Stage 1 uses Node to compile the Angular app into static files.
#   Stage 2 copies only those files into a tiny nginx image. Node, node_modules and the
#   TypeScript sources are left behind, so the final image is ~50 MB instead of ~1 GB.
# =====================================================================================

# ---------- Stage 1: compile the Angular app ----------
# Node 18 matches the Angular 17 toolchain used in development and CI.
FROM node:18-alpine AS build
WORKDIR /app
# Copy only the dependency manifests first. Docker caches each layer, so "npm ci" is
# re-run only when package*.json change - not on every source edit.
COPY package.json package-lock.json ./
# "npm ci" installs exactly what package-lock.json says (reproducible builds).
RUN npm ci
COPY . .
# Production build: minified, tree-shaken, hashed file names for cache busting.
RUN npx ng build --configuration production

# ---------- Stage 2: serve the static files with nginx ----------
FROM nginx:1.27-alpine
# Our server config (static files + /api reverse proxy) replaces nginx's default site.
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Angular 17's application builder writes the browser bundle to dist/<project>/browser.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
# The base image's default command already starts nginx in the foreground.
