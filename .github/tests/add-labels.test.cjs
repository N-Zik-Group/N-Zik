const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');

const workflow = readFileSync(join(__dirname, '../workflows/add-labels.yaml'), 'utf8');
const scriptBlock = workflow.match(/^          script: \|\r?\n((?:            [^\r\n]*\r?\n|\r?\n)+)/m);
assert.ok(scriptBlock, 'Expected the actual github-script inline script');
const script = scriptBlock[1].replace(/^            /gm, '');
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const execute = new AsyncFunction('github', 'context', 'console', script);
const bodyFor = (value) => `### 🏗️ Build Type\n\n${value}\n\n### Other field\n\nOther answer`;

function harness({ body = bodyFor('Release64'), labels = [], issues, pullRequest = false,
  addError, removeErrors = new Map() } = {}) {
  const calls = [];
  const issueData = issues || [{ number: 42, body, ...(pullRequest ? { pull_request: {} } : {}) }];
  const labelsByIssue = new Map(issueData.map((issue) => [issue.number, [...(issue.labels || labels)]]));
  const rest = {
    issues: {
      async listForRepo(params) {
        calls.push({ method: 'listForRepo', ...params });
        const start = ((params.page || 1) - 1) * params.per_page;
        return { data: issueData.slice(start, start + params.per_page) };
      },
      async listLabelsOnIssue(params) {
        calls.push({ method: 'listLabelsOnIssue', ...params });
        const names = labelsByIssue.get(params.issue_number);
        const perPage = params.per_page || 30;
        const start = ((params.page || 1) - 1) * perPage;
        return { data: names.slice(start, start + perPage).map((name) => ({ name })) };
      },
      async removeLabel(params) {
        calls.push({ method: 'removeLabel', ...params });
        const names = labelsByIssue.get(params.issue_number);
        const status = removeErrors.get(params.name);
        if (status) {
          if (status === 404) {
            labelsByIssue.set(params.issue_number, names.filter((name) => name !== params.name));
          }
          throw Object.assign(new Error('Mock removal failure'), { status });
        }
        assert.ok(names.includes(params.name), 'Cannot remove a missing label');
        labelsByIssue.set(params.issue_number, names.filter((name) => name !== params.name));
      },
      async addLabels(params) {
        calls.push({ method: 'addLabels', ...params });
        if (addError) throw addError;
        labelsByIssue.get(params.issue_number).push(...params.labels);
      },
    },
  };
  const github = {
    rest,
    async paginate(method, params) {
      const result = [];
      for (let page = 1; ; page++) {
        const { data } = await method({ ...params, page });
        result.push(...data);
        if (data.length < params.per_page) return result;
      }
    },
  };
  const context = {
    repo: { owner: 'owner', repo: 'repo' },
    eventName: issues ? 'workflow_dispatch' : 'issues',
    issue: { number: 42 },
    payload: { issue: issueData[0] },
  };
  return {
    calls,
    labelsByIssue,
    mutations: () => calls.filter(({ method }) => ['addLabels', 'removeLabel'].includes(method)),
    run: () => execute(github, context, { log() {} }),
  };
}

const variants = [
  ['Release64', 'release64'], ['Release32', 'release32'],
  ['Minified64', 'minified64'], ['Minified32', 'minified32'],
  ['Beta64', 'beta64'], ['Beta32', 'beta32'],
  ['Dev64', 'dev64'], ['Dev32', 'dev32'],
  ['Foss (N-Zik-Foss, com.nevar.nzik.foss)', 'foss'],
  ['Full', 'release64'], ['Full32', 'release32'],
  ['Minified', 'minified64'], ['Beta', 'beta64'], ['Dev', 'dev64'],
];

for (const [value, expected] of variants) {
  test(`assigns canonical ${expected} for ${value}`, async () => {
    const run = harness({ body: bodyFor(value), labels: ['bug'] });
    await run.run();
    assert.deepEqual(run.labelsByIssue.get(42), ['bug', expected]);
    assert.deepEqual(run.mutations().map(({ method, labels }) => ({ method, labels })), [
      { method: 'addLabels', labels: [expected] },
    ]);
  });
}

test('accepts CRLF, mixed case and whitespace in an actual form field', async () => {
  const run = harness({ body: bodyFor('  bEtA32  ').replaceAll('\n', '\r\n') });
  await run.run();
  assert.deepEqual(run.labelsByIssue.get(42), ['beta32']);
});

test('accepts the Build Type heading without its optional emoji variation selector', async () => {
  for (const heading of ['### 🏗 Build Type', '### Build Type']) {
    const run = harness({ body: `${heading}\n\nFull32` });
    await run.run();
    assert.deepEqual(run.labelsByIssue.get(42), ['release32']);
  }
});

test('unknown or missing fields cause no label API calls or mutations', async () => {
  const bodies = [
    null, '', '### Other field\n\nRelease64', bodyFor('_No response_'),
    bodyFor('Full320'), bodyFor('Beta64foo'), bodyFor('Fossil'),
    bodyFor('Release64-extra'), bodyFor('Beta64 (valid) suffix'), bodyFor('constructor'),
    '### 🏗️ Build Type\n\n### Other field\n\nFull',
    '### 🏗️ Build Type notes\n\nFull', 'Text ### 🏗️ Build Type\n\nFull',
    '#### 🏗️ Build Type\n\nFull', '### 🏗️ Build Type\n\nUnknown\nFull',
    '### 🏗️ Build Type\n\n\n### Other field\nFull',
  ];
  for (const body of bodies) {
    const run = harness({ body, labels: ['bug', 'full', 'beta32'] });
    await run.run();
    assert.deepEqual(run.calls, [], `Unexpected API call for ${JSON.stringify(body)}`);
    assert.deepEqual(run.labelsByIssue.get(42), ['bug', 'full', 'beta32']);
  }
});

test('removes every conflicting canonical and legacy build label, preserving unrelated labels', async () => {
  const buildLabels = [
    'release64', 'release32', 'minified64', 'minified32', 'beta64', 'beta32',
    'foss', 'dev64', 'dev32', 'full', 'full32', 'minified', 'beta', 'dev',
  ];
  const unrelated = ['bug', 'patched STABLE', 'patched BETA', 'patched DEV', 'full-custom'];
  const run = harness({ body: bodyFor('Full32'), labels: [...unrelated, ...buildLabels] });
  await run.run();
  assert.deepEqual(run.labelsByIssue.get(42), [...unrelated, 'release32']);
  assert.equal(run.mutations().length, buildLabels.length - 1);
  assert.ok(run.mutations().every(({ method }) => method === 'removeLabel'));
});

test('paginates labels before cleanup and does not add a canonical label already on a later page', async () => {
  const unrelated = Array.from({ length: 100 }, (_, index) => `custom-${index}`);
  const run = harness({ body: bodyFor('Dev64'), labels: [...unrelated, 'dev', 'beta32', 'dev64'] });
  await run.run();
  assert.deepEqual(run.labelsByIssue.get(42), [...unrelated, 'dev64']);
  assert.deepEqual(run.calls.filter(({ method }) => method === 'listLabelsOnIssue').map(({ page }) => page), [1, 2]);
  assert.deepEqual(run.mutations().map(({ method, name }) => ({ method, name })), [
    { method: 'removeLabel', name: 'dev' }, { method: 'removeLabel', name: 'beta32' },
  ]);
});

test('repeated issue edits are idempotent after alias cleanup', async () => {
  const run = harness({ body: bodyFor('Beta'), labels: ['bug', 'beta', 'full32'] });
  await run.run();
  const firstMutationCount = run.mutations().length;
  await run.run();
  assert.equal(firstMutationCount, 3);
  assert.equal(run.mutations().length, firstMutationCount);
  assert.deepEqual(run.labelsByIssue.get(42), ['bug', 'beta64']);
});

test('adds the canonical label before cleaning conflicting labels', async () => {
  const run = harness({ body: bodyFor('Beta'), labels: ['bug', 'full', 'dev32'] });
  await run.run();
  assert.deepEqual(run.mutations().map(({ method }) => method), ['addLabels', 'removeLabel', 'removeLabel']);
  assert.deepEqual(run.labelsByIssue.get(42), ['bug', 'beta64']);
});

test('a failed add leaves every existing label untouched and propagates the error', async () => {
  const addError = Object.assign(new Error('Mock add failure'), { status: 403 });
  const labels = ['bug', 'full', 'dev32'];
  const run = harness({ body: bodyFor('Beta'), labels, addError });
  await assert.rejects(run.run(), (error) => error === addError);
  assert.deepEqual(run.labelsByIssue.get(42), labels);
  assert.deepEqual(run.mutations().map(({ method }) => method), ['addLabels']);
});

test('existing labels compare without case but removals use their original names', async () => {
  const run = harness({ body: bodyFor('Beta'), labels: ['Bug', 'BeTa64', 'FuLl32', 'DeV32'] });
  await run.run();
  assert.deepEqual(run.labelsByIssue.get(42), ['Bug', 'BeTa64']);
  assert.deepEqual(run.mutations().map(({ method, name }) => ({ method, name })), [
    { method: 'removeLabel', name: 'FuLl32' }, { method: 'removeLabel', name: 'DeV32' },
  ]);
  const mutationCount = run.mutations().length;
  await run.run();
  assert.equal(run.mutations().length, mutationCount);
});

test('a concurrent removal returning 404 allows remaining cleanup and backfill to continue', async () => {
  const run = harness({
    issues: [
      { number: 1, body: bodyFor('Beta'), labels: ['full', 'dev32'] },
      { number: 2, body: bodyFor('Release32'), labels: ['bug'] },
    ],
    removeErrors: new Map([['full', 404]]),
  });
  await run.run();
  assert.deepEqual(run.labelsByIssue.get(1), ['beta64']);
  assert.deepEqual(run.labelsByIssue.get(2), ['bug', 'release32']);
});

test('non-404 removal failures propagate and stop the backfill', async () => {
  for (const status of [403, 429, 500]) {
    const run = harness({
      issues: [
        { number: 1, body: bodyFor('Beta'), labels: ['full', 'dev32'] },
        { number: 2, body: bodyFor('Release32'), labels: ['bug'] },
      ],
      removeErrors: new Map([['full', status]]),
    });
    await assert.rejects(run.run(), (error) => error.status === status);
    assert.deepEqual(run.labelsByIssue.get(1), ['full', 'dev32', 'beta64']);
    assert.deepEqual(run.labelsByIssue.get(2), ['bug']);
    assert.deepEqual(run.mutations().map(({ method }) => method), ['addLabels', 'removeLabel']);
  }
});

test('manual dispatch paginates open and closed issues and skips PRs and unknown builds', async () => {
  const issues = Array.from({ length: 100 }, (_, index) => ({
    number: index + 1, body: bodyFor('Full'), pull_request: {}, labels: ['bug', 'full'],
  }));
  issues.push(
    { number: 101, state: 'closed', body: bodyFor('Minified'), labels: ['bug', 'minified'] },
    { number: 102, state: 'open', body: bodyFor('Dev32'), labels: ['open'] },
    { number: 103, body: bodyFor('Unknown'), labels: ['beta'] },
  );
  const run = harness({ issues });
  await run.run();
  const issueReads = run.calls.filter(({ method }) => method === 'listForRepo');
  assert.deepEqual(issueReads.map(({ page }) => page), [1, 2]);
  assert.ok(issueReads.every(({ state, per_page }) => state === 'all' && per_page === 100));
  assert.deepEqual(run.labelsByIssue.get(1), ['bug', 'full']);
  assert.deepEqual(run.labelsByIssue.get(101), ['bug', 'minified64']);
  assert.deepEqual(run.labelsByIssue.get(102), ['open', 'dev32']);
  assert.deepEqual(run.labelsByIssue.get(103), ['beta']);
  assert.ok(run.calls.filter(({ method }) => method === 'listLabelsOnIssue')
    .every(({ issue_number }) => [101, 102].includes(issue_number)));
});

test('a pull request payload never changes labels', async () => {
  const run = harness({ pullRequest: true, labels: ['full'] });
  await run.run();
  assert.deepEqual(run.calls, []);
  assert.deepEqual(run.labelsByIssue.get(42), ['full']);
});

test('all mocked GitHub API calls retain the target repository and issue number', async () => {
  const run = harness({ labels: ['full'] });
  await run.run();
  for (const call of run.calls) {
    assert.equal(call.owner, 'owner');
    assert.equal(call.repo, 'repo');
    assert.equal(call.issue_number, 42);
  }
});
