const http = require('http');
const { execFile } = require('child_process');

const PORT = process.env.PORT || 8765;

const server = http.createServer((req, res) => {
    if (req.method !== 'POST' || req.url !== '/ask') {
        res.writeHead(404);
        res.end();
        return;
    }
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
        const { systemPrompt, userMessage } = JSON.parse(body);
        execFile(
            'claude',
            ['-p', userMessage, '--system-prompt', systemPrompt, '--dangerously-skip-permissions'],
            { stdio: ['ignore', 'pipe', 'pipe'] },
            (err, stdout, stderr) => {
                if (err) {
                    res.writeHead(500);
                    res.end(stderr || err.message);
                    return;
                }
                res.writeHead(200, { 'Content-Type': 'text/plain' });
                res.end(stdout);
            }
        );
    });
});

server.listen(PORT, () => console.log(`Claude proxy listening on :${PORT}`));
