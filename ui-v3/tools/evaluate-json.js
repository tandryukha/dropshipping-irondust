#!/usr/bin/env node
/*
  Minimal evaluator for MCP pipelines:
  - Reads a JSON object from stdin or first CLI arg path
  - Expects shape: { "prompt": string, "result": any, "criteria": { name: string, check: string }[] }
  - Emits JSON with pass/fail per criterion and overall score
*/
const fs = require('fs');

async function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', chunk => (data += chunk));
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

async function main() {
  let inputStr = '';
  const argPath = process.argv[2];
  if (argPath) {
    inputStr = fs.readFileSync(argPath, 'utf8');
  } else {
    inputStr = await readStdin();
  }
  if (!inputStr.trim()) {
    console.error('No input provided.');
    process.exit(2);
  }
  const input = JSON.parse(inputStr);
  const { prompt, result, criteria = [] } = input;

  const evals = criteria.map(c => {
    // c.check is a small JS expression evaluated with access to prompt, result
    // Example: "Array.isArray(result) && result.length > 0"
    let pass = false;
    let error = null;
    try {
      // eslint-disable-next-line no-new-func
      const fn = new Function('prompt', 'result', `return (${c.check});`);
      pass = !!fn(prompt, result);
    } catch (e) {
      error = String(e && e.message ? e.message : e);
    }
    return { name: c.name || 'criterion', pass, error };
  });

  const score = evals.length ? evals.filter(e => e.pass).length / evals.length : 0;
  const output = { ok: score === 1, score, evals };
  process.stdout.write(JSON.stringify(output));
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});


