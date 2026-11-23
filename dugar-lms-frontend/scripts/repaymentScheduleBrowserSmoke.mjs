// Local browser smoke fixture: serves the real API client and PDF-opening helper.
// Requires the synthetic PDF produced by run_repayment_pdf_integration.py.
import http from 'node:http';
import fs from 'node:fs';
const root = new URL('../', import.meta.url);
const read = path => fs.readFileSync(new URL(path, root));
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  response.setHeader('Cache-Control', 'no-store');
  if (url.pathname === '/') {
    response.setHeader('Content-Type', 'text/html');
    response.end(`<!doctype html><html><title>Local repayment schedule smoke test</title>
      <body><h1>Local repayment schedule smoke test</h1><p>Synthetic contract 17270</p>
      <button id="generate">Generate Repayment Schedule</button><p id="status"></p>
      <script type="module">
      import { openRepaymentSchedulePdf } from '/src/services/repaymentScheduleService.js';
      localStorage.setItem('token','local-test-token');
      document.querySelector('#generate').onclick = async () => {
        try { await openRepaymentSchedulePdf(6535); document.querySelector('#status').textContent='PDF opened in a new tab'; }
        catch(error) { document.querySelector('#status').textContent=error.message; }
      };
      </script></body></html>`);
  } else if (url.pathname === '/src/services/repaymentScheduleService.js') {
    response.setHeader('Content-Type', 'text/javascript'); response.end(read('src/services/repaymentScheduleService.js'));
  } else if (url.pathname === '/src/api/apiClient') {
    response.setHeader('Content-Type', 'text/javascript');
    response.end(read('src/api/apiClient.js').toString().replace("from 'axios'", "from '/axios.js'"));
  } else if (url.pathname === '/axios.js') {
    response.setHeader('Content-Type', 'text/javascript'); response.end(read('node_modules/axios/dist/esm/axios.js'));
  } else if (url.pathname === '/api/contracts/6535/repayment-schedule/pdf') {
    if (request.headers.authorization !== 'Bearer local-test-token') {
      response.writeHead(401, { 'Content-Type': 'application/json' }); response.end('{"message":"Test authorization required"}'); return;
    }
    console.log('Authenticated PDF request: PASS');
    response.setHeader('Content-Type', 'application/pdf');
    response.setHeader('Content-Disposition', 'inline; filename="Repayment_Schedule_17270.pdf"');
    response.end(read('../dugar-lms-api/target/repayment-schedule-preview/repayment-schedule-17270.pdf'));
  } else { response.writeHead(404); response.end(); }
});
server.listen(0, '127.0.0.1', () => console.log(`Browser smoke fixture: http://127.0.0.1:${server.address().port}/`));
