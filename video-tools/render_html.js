// node render_html.js page.html out.png [width] [height]
// Renders an HTML slide to PNG with the preinstalled Chromium (fonts come from fontconfig).
const { chromium } = require(process.env.PW_PATH || '/opt/node22/lib/node_modules/playwright');
const path = require('path');
(async () => {
  const [html, out, w = '1920', h = '1080'] = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: +w, height: +h } });
  await page.goto('file://' + path.resolve(html));
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: out });
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
