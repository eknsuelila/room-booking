# Room Booking – Angular frontend

Angular 17 (standalone components, signals, new control flow). See the main
[README](../README.md) and [docs/HOW-IT-WORKS.md](../docs/HOW-IT-WORKS.md).

```bash
npm install
npm start            # ng serve on http://localhost:4304, proxies /api to http://localhost:8084
npm test             # Karma + Jasmine unit tests
npx ng build         # production build into dist/frontend/browser
```

Headless test run (no browser window):

```bash
CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  npx ng test --watch=false --browsers=ChromeHeadless
```
