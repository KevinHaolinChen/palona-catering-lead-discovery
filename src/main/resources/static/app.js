const restaurantSearchForm = document.getElementById('restaurantSearchForm');
const restaurantSearch = document.getElementById('restaurantSearch');
const searchButton = document.getElementById('searchButton');
const restaurantSearchResults = document.getElementById('restaurantSearchResults');
const selectedRestaurant = document.getElementById('selectedRestaurant');
const selectedRestaurantName = document.getElementById('selectedRestaurantName');
const selectedRestaurantAddress = document.getElementById('selectedRestaurantAddress');
const changeRestaurant = document.getElementById('changeRestaurant');
const campaignForm = document.getElementById('campaignForm');
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
  selectedRestaurant: null,
  restaurantMatches: [],
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

function inferRestaurantType(match) {
  const category = String(match?.category || '').toLowerCase();
  const name = String(match?.name || '').toLowerCase();
  if (category.includes('cafe') || category.includes('bakery')) return 'cafe_bakery';
  if (category.includes('fast_food')) return 'fast_casual';
  if (name.includes('pizza') || name.includes('pizzeria')) return 'pizza';
  if (name.includes('breakfast') || name.includes('brunch')) return 'breakfast_brunch';
  return 'full_service';
}

function renderRestaurantMatches(matches) {
  state.restaurantMatches = matches;
  if (!matches.length) {
    restaurantSearchResults.hidden = false;
    restaurantSearchResults.innerHTML =
      '<div class="location-empty">No restaurant locations found. Try adding a city or ZIP code.</div>';
    return;
  }

  restaurantSearchResults.hidden = false;
  restaurantSearchResults.innerHTML = matches.map((match, index) => `
    <button class="location-option" type="button" data-index="${index}">
      <span class="location-pin">⌖</span>
      <span class="location-copy">
        <strong>${escapeHtml(match.name)}</strong>
        <small>${escapeHtml(match.address)}</small>
      </span>
      <span class="location-arrow">→</span>
    </button>
  `).join('');
}

function selectRestaurant(index) {
  const match = state.restaurantMatches[index];
  if (!match) return;

  state.selectedRestaurant = match;
  selectedRestaurantName.textContent = match.name;
  selectedRestaurantAddress.textContent = match.address;
  selectedRestaurant.hidden = false;
  restaurantSearchResults.hidden = true;
  document.getElementById('restaurantType').value = inferRestaurantType(match);
  setStatus(`Selected ${match.name}. Choose a radius and run discovery.`, 'success');
}

async function handleRestaurantSearch(event) {
  event.preventDefault();
  const query = restaurantSearch.value.trim();
  if (query.length < 2) {
    setStatus('Enter a restaurant or franchise name.', 'error');
    return;
  }

  state.selectedRestaurant = null;
  selectedRestaurant.hidden = true;
  searchButton.disabled = true;
  searchButton.textContent = 'Searching…';
  setStatus('Searching restaurant locations…');

  try {
    const matches = await request(`/api/restaurants/search?q=${encodeURIComponent(query)}`);
    renderRestaurantMatches(matches);
    setStatus(
      matches.length
        ? `Found ${matches.length} possible location${matches.length === 1 ? '' : 's'}. Choose one below.`
        : 'No matching restaurant locations found.'
    );
  } catch (error) {
    setStatus(error.message || 'Restaurant search failed.', 'error');
  } finally {
    searchButton.disabled = false;
    searchButton.textContent = 'Search';
  }
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
    source ? `<a class="action-link" href="${escapeHtml(source)}" target="_blank" rel="noreferrer">Evidence ↗</a>` : '',
  ].join('');

  return `
    <article class="prospect-card">
      <div class="score-ring"><div><strong>${prospect.score}</strong><span>/100</span></div></div>
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
  const average = count ? Math.round(prospects.reduce((sum, p) => sum + p.score, 0) / count) : 0;
  const nearest = count ? Math.min(...prospects.map(p => Number(p.distance_miles))) : null;

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
  for (let attempt = 0; attempt < 55; attempt += 1) {
    const run = await request(`/api/runs/${encodeURIComponent(runId)}`);
    state.run = run;
    setRunBadge(run.status);

    if (run.status === 'COMPLETED') return run;
    if (run.status === 'FAILED') throw new Error(run.error_message || 'Discovery run failed.');

    setStatus(
      run.status === 'QUEUED'
        ? 'Preparing discovery providers…'
        : 'Scanning nearby organizations and ranking prospect signals…'
    );
    await delay(1200);
  }

  throw new Error('Discovery is taking longer than expected. Try a smaller radius or run again.');
}

async function handleCampaignSubmit(event) {
  event.preventDefault();
  const restaurant = state.selectedRestaurant;
  if (!restaurant) {
    setStatus('Search for your restaurant and choose a location first.', 'error');
    restaurantSearch.focus();
    return;
  }

  const restaurantType = document.getElementById('restaurantType').value;
  const radiusMeters = Number(document.getElementById('radius').value);

  runButton.disabled = true;
  runButton.querySelector('span:first-child').textContent = 'Building prospect map…';
  setRunBadge('QUEUED');
  setStatus('Creating restaurant campaign…');

  try {
    const campaign = await request('/api/campaigns', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: restaurant.name,
        business_type: restaurantType,
        address: restaurant.address,
        latitude: restaurant.latitude,
        longitude: restaurant.longitude,
        radius_meters: radiusMeters,
      }),
    });

    state.campaign = campaign;
    resultsTitle.textContent = `AI-ranked prospects for ${campaign.name}`;
    resultsSubtitle.textContent =
      `${campaign.address} · ${(campaign.radius_meters / 1609.344).toFixed(0)}-mile search radius`;

    const run = await request(`/api/campaigns/${encodeURIComponent(campaign.id)}/runs`, { method: 'POST' });
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
    runButton.querySelector('span:first-child').textContent = 'Find AI-ranked prospects';
  }
}

restaurantSearchForm.addEventListener('submit', handleRestaurantSearch);
restaurantSearchResults.addEventListener('click', event => {
  const option = event.target.closest('[data-index]');
  if (option) selectRestaurant(Number(option.dataset.index));
});
changeRestaurant.addEventListener('click', () => {
  state.selectedRestaurant = null;
  selectedRestaurant.hidden = true;
  restaurantSearchResults.hidden = true;
  restaurantSearch.focus();
  setStatus('Search for another restaurant location.');
});
restaurantSearch.addEventListener('input', () => {
  if (state.selectedRestaurant) {
    state.selectedRestaurant = null;
    selectedRestaurant.hidden = true;
  }
});
campaignForm.addEventListener('submit', handleCampaignSubmit);
minScore.addEventListener('input', renderProspects);
sortBy.addEventListener('change', renderProspects);
