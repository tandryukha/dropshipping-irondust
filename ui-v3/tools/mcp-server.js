#!/usr/bin/env node
const { Server } = require('@modelcontextprotocol/sdk/server/index');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio');
const cp = require('node:child_process');
const path = require('node:path');

const server = new Server({ name: 'supplements-mockup-mcp', version: '0.1.0' });

function runCmd(cmd) {
  return new Promise((resolve) => {
    cp.exec(cmd, { cwd: path.resolve(__dirname, '..') }, (err, stdout, stderr) => {
      resolve({ ok: !err, code: err ? err.code : 0, stdout, stderr });
    });
  });
}

server.tool('runTests', { description: 'Run Playwright test suite' }, async () => {
  const res = await runCmd('npm run -s test');
  return {
    content: [{ type: 'text', text: JSON.stringify(res) }],
    isError: !res.ok,
  };
});

server.tool('updateSnapshots', { description: 'Update Playwright snapshots' }, async () => {
  const res = await runCmd('npm run -s test:update');
  return {
    content: [{ type: 'text', text: JSON.stringify(res) }],
    isError: !res.ok,
  };
});

server.tool('evaluateJson', { description: 'Run JSON evaluator script (stdin or file path arg)' }, async (args) => {
  const fileArg = args?.arguments?.path ? ` ${args.arguments.path}` : '';
  const cmd = `node tools/evaluate-json.js${fileArg}`;
  const res = await runCmd(cmd);
  return {
    content: [{ type: 'text', text: JSON.stringify(res) }],
    isError: !res.ok,
  };
});

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});


