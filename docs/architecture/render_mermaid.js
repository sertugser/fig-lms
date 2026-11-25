// Renders mermaid code to SVG in headless Chromium. Used by build_page.py.
// Usage: node render_mermaid.js <mermaid.min.js> <in.json: ["code", ...]> <out.json>
const fs = require('fs');
const { chromium } = require('playwright');

(async () => {
  const [mermaidJs, inFile, outFile] = process.argv.slice(2);
  const codes = JSON.parse(fs.readFileSync(inFile, 'utf8'));
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent('<html><body></body></html>');
  await page.addScriptTag({ path: mermaidJs });
  const svgs = await page.evaluate(async (codes) => {
    mermaid.initialize({
      startOnLoad: false, theme: 'base', securityLevel: 'strict',
      fontFamily: 'Segoe UI, Helvetica, Arial, sans-serif',
      flowchart: { htmlLabels: true, curve: 'basis', useMaxWidth: true, wrappingWidth: 420, padding: 14 },
      sequence: { useMaxWidth: true, mirrorActors: false, actorMargin: 24, width: 165, wrap: true, messageFontSize: 15, noteFontSize: 15, actorFontSize: 15, boxMargin: 8 },
      themeVariables: {
        primaryColor: '#eef3f8', primaryBorderColor: '#5b7289', primaryTextColor: '#1b2733',
        lineColor: '#5b7289', clusterBkg: '#f7f9fb', clusterBorder: '#a9b8c6', fontSize: '15px',
        actorBkg: '#eef3f8', actorBorder: '#5b7289', noteBkgColor: '#fff4d6', noteBorderColor: '#d9a520',
      },
    });
    const out = [];
    for (let i = 0; i < codes.length; i++) {
      const { svg } = await mermaid.render('d' + i, codes[i]);
      out.push(svg);
    }
    return out;
  }, codes);
  fs.writeFileSync(outFile, JSON.stringify(svgs));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
