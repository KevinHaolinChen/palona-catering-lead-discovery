const form = document.getElementById('campaignForm');
const runButton = document.getElementById('runButton');
const formStatus = document.getElementById('formStatus');
const runBadge = document.getElementById('runBadge');
const resultsTitle = document.getElementById('resultsTitle');
const resultsSubtitle = document.getElementById('resultsSubtitle');
const metrics = document.getElementById('metrics');
const resultControls = document.getElementById('resultControls');
const emptyState = document.getElementById('emptyState');
const prospectList = document.getElementById('prospects');
const minScore = document.getElementById('minScore');
const scoreValue = document.getElementById('scoreValue');
const sortBy = document.getElementById('sortBy');

const state = {
  campaign: null,
  run: null,
  prospects: [],
};

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function safeHttpUrl(value) {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

async function request(url, options = {}) {
  const response = await fetch(url, options);
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message = body?.detail || body?.message || body?.title || body || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return body;
}

function setStatus(message, kind = '') {
  formStatus.textContent = message;
  formStatus.className = `form-status ${kind}`.trim();
}

function setRunBadge(status) {
  const normalized = status || 'IDLE';
  runBadge.textContent = normalized;
  runBadge.className = 'run-badge';
  if (['QUEUED', 'DISCOVERING'].includes(normalized)) runBadge.classList.add('working');
  else if (normalized === 'COMPLETED') runBadge.classList.add('complete');
  else if (normalized === 'FAILED') runBadge.classList.add('failed');
  else runBadge.classList.add('idle');
}

function humanize(value) {
  return String(value || 'organization')
    .replaceAll('_', ' ')
    .replace(/\b\w/g, char => char.toUpperCase());
}

function parseScoreExplanation(value) {
  return String(value || '')
    .split(',')
    .map(part => part.trim())
    .filter(Boolean)
    .map(part => {
      const [key, score] = part.split('=');
      return `<span>${escapeHtml(humanize(key))} ${escapeHtml(score)}</span>`;
    })
    .join('');
}

function prospectCard(prospect) {
  const website = safeHttpUrl(prospect.website);
  const source = safeHttpUrl(prospect.source_url);
  const phone = prospect.phone ? String(prospect.phone).trim() : '';
  const phoneHref = phone ? `tel:${phone.replace(/[^+\d]/g, '')}` : null;

  const actions = [
    website ? `<a class="action-link" href="${escapeHtml(website)}" target="_blank" rel="noreferrer">Website ↗</a>` : '',
    phoneHref ? `<a class="action-link" href="${escapeHtml(phoneHref)}">Call</a>` : '',
    source ? `<a class="action-link" href="${escapeHtml(source)}" target="_blank" rel="noreferrer">Source ↗</a>` : '',
  ].join('');

  return `
    <article class="prospect-card">
      <div class="score-ring">
        <div><strong>${prospect.score}</strong><span>/100</span></div>
      </div>

      <div class="prospect-main">
        <div class="prospect-topline">
          <span class="category-chip">${escapeHtml(humanize(prospect.category))}</span>
          <span class="signal-chip">${escapeHtml(Number(prospect.distance_miles).toFixed(1))} mi away</span>
          ${prospect.source_name ? `<span class="signal-chip">${escapeHtml(prospect.source_name)}</span>` : ''}
        </div>
        <h3>${escapeHtml(prospect.organization)}</h3>
        <div class="prospect-meta">
          <span>${escapeHtml(prospect.address || 'Address unavailable')}</span>
          ${phone ? `<span>${escapeHtml(phone)}</span>` : ''}
        </div>
        <div class="score-explanation">${parseScoreExplanation(prospect.score_explanation)}</div>
      </div>

      <div class="prospect-actions">${actions}</div>
    </article>
  `;
}

function renderMetrics() {
  const prospects = state.prospects;
  const count = prospects.length;
  const qualified = prospects.filter(p => p.score >= 70).length;
  const average = count
    ? Math.round(prospects.reduce((sum, p) => sum + p.score, 0) / count)
    : 0;
  const nearest = count
    ? Math.min(...prospects.map(p => Number(p.distance_miles)))
    : null;

  document.getElementById('metricCount').textContent = count;
  document.getElementById('metricQualified').textContent = qualified;
  document.getElementById('metricAverage').textContent = average;
  document.getElementById('metricNearest').textContent = nearest == null ? '—' : `${nearest.toFixed(1)} mi`;
}

function renderProspects() {
  const threshold = Number(minScore.value);
  scoreValue.textContent = threshold;

  let visible = state.prospects.filter(p => p.score >= threshold);
  const sort = sortBy.value;

  visible = [...visible].sort((a, b) => {
    if (sort === 'distance') return Number(a.distance_miles) - Number(b.distance_miles);
    if (sort === 'name') return String(a.organization).localeCompare(String(b.organization));
    return b.score - a.score || Number(a.distance_miles) - Number(b.distance_miles);
  });

  prospectList.innerHTML = visible.length
    ? visible.map(prospectCard).join('')
    : '<div class="no-results">No prospects match the current score threshold.</div>';
}

function revealResults() {
  emptyState.hidden = true;
  metrics.hidden = false;
  resultControls.hidden = false;
  renderMetrics();
  renderProspects();
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitForRun(runId) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const run = await request(`/api/runs/${encodeURIComponent(runId)}`);
    state.run = run;
    setRunBadge(run.status);

    if (run.status === 'COMPLETED') return run;
    if (run.status === 'FAILED') {
      throw new Error(run.error_message || 'Discovery run failed.');
    }

    setStatus(
      run.status === 'QUEUED'
        ? 'Discovery queued. Preparing nearby sources…'
        : 'Scanning nearby organizations and scoring candidates…'
    );
    await delay(1200);
  }

  throw new Error('Discovery is taking longer than expected. Check the server logs and try again.');
}

async function handleSubmit(event) {
  event.preventDefault();

  const restaurantName = document.getElementById('restaurantName').value.trim();
  const restaurantType = document.getElementById('restaurantType').value;
  const address = document.getElementById('restaurantAddress').value.trim();
  const radiusMeters = Number(document.getElementById('radius').value);

  runButton.disabled = true;
  runButton.querySelector('span:first-child').textContent = 'Building lead list…';
  setRunBadge('QUEUED');
  setStatus('Locating the restaurant address…');

  try {
    const geocode = await request(`/api/geocode?address=${encodeURIComponent(address)}`);

    setStatus(`Located: ${geocode.display_name}. Creating campaign…`);

    const campaign = await request('/api/campaigns', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: restaurantName,
        business_type: restaurantType,
        address: geocode.display_name,
        latitude: geocode.latitude,
        longitude: geocode.longitude,
        radius_meters: radiusMeters,
      }),
    });

    state.campaign = campaign;
    resultsTitle.textContent = `Prospects for ${campaign.name}`;
    resultsSubtitle.textContent =
      `${campaign.address} · ${(campaign.radius_meters / 1609.344).toFixed(0)}-mile search radius`;

    const run = await request(`/api/campaigns/${encodeURIComponent(campaign.id)}/runs`, {
      method: 'POST',
    });

    state.run = run;
    await waitForRun(run.id);

    state.prospects = await request(`/api/runs/${encodeURIComponent(run.id)}/prospects`);
    setStatus(
      `Complete. Found ${state.prospects.length} ranked organizations around ${campaign.name}.`,
      'success'
    );
    revealResults();
  } catch (error) {
    setRunBadge('FAILED');
    setStatus(error.message || 'Something went wrong.', 'error');
  } finally {
    runButton.disabled = false;
    runButton.querySelector('span:first-child').textContent = 'Find prospects';
  }
}

form.addEventListener('submit', handleSubmit);
minScore.addEventListener('input', renderProspects);
sortBy.addEventListener('change', renderProspects);
