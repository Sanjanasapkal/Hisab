const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const app = require('../src/app');

// Helper to make test HTTP requests against Express app without starting a full server on a port
function request(app, options) {
    return new Promise((resolve, reject) => {
        const server = http.createServer(app);
        server.listen(0, () => {
            const port = server.address().port;
            const req = http.request({
                port,
                host: '127.0.0.1',
                ...options
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    server.close();
                    try {
                        const parsed = JSON.parse(data);
                        resolve({ status: res.statusCode, headers: res.headers, body: parsed });
                    } catch (e) {
                        resolve({ status: res.statusCode, headers: res.headers, body: data });
                    }
                });
            });
            req.on('error', (err) => {
                server.close();
                reject(err);
            });
            if (options.body) {
                req.write(typeof options.body === 'string' ? options.body : JSON.stringify(options.body));
            }
            req.end();
        });
    });
}

test('GET / returns 200 and basic API info', async () => {
    const res = await request(app, { path: '/', method: 'GET' });
    assert.equal(res.status, 200);
    assert.equal(res.body.success, true);
    assert.equal(res.body.message, 'Hisab Backend API is running');
    assert.equal(res.body.version, '1.0.0');
});

test('GET /health returns health check structure', async () => {
    const res = await request(app, { path: '/health', method: 'GET' });
    // In test environment without MongoDB connected, status is 503 degraded
    assert.ok(res.status === 200 || res.status === 503);
    assert.ok(res.body.database);
    assert.ok(typeof res.body.uptimeSeconds === 'number');
});

test('GET /unknown-endpoint returns 404 with structured error', async () => {
    const res = await request(app, { path: '/api/unknown', method: 'GET' });
    assert.equal(res.status, 404);
    assert.equal(res.body.success, false);
    assert.match(res.body.message, /Endpoint not found/);
});
