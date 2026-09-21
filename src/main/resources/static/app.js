const restaurantSearchForm = document.getElementById('restaurantSearchForm');
const restaurantSearch = document.getElementById('restaurantSearch');
const restaurantSearchResults = document.getElementById('restaurantSearchResults');
const locationIndicator = document.getElementById('locationIndicator');
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
const radius = document.getElementById('radius');
const radiusValue = document.getElementById('radiusValue');
const aiStatusPill = document.getElementById('aiStatusPill');
const aiStatusText = document.getElementById('aiStatusText');
const supportsCatering = document.getElementById('supportsCatering');
const primaryDaypart = document.getElementById('primaryDaypart');
const priceTier = document.getElementById('priceTier');
const deliveryRadius = document.getElementById('deliveryRadius');
const deliveryRadiusValue = document.getElementById('deliveryRadiusValue');
const mapSection = document.getElementById('mapSection');
const mapStatus = document.getElementById('mapStatus');
const workspaceList = document.getElementById('workspaceList');
const salesLoop = document.getElementById('salesLoop');
const refreshRadiusButton = document.getElementById('refreshRadiusButton');
const watchEnabled = document.getElementById('watchEnabled');
const watchInterval = document.getElementById('watchInterval');
const pipelineDue = document.getElementById('pipelineDue');
const pipelineNew = document.getElementById('pipelineNew');
const pipelineContacted = document.getElementById('pipelineContacted');
const pipelineReplied = document.getElementById('pipelineReplied');
const pipelineWon = document.getElementById('pipelineWon');
const learningSummary = document.getElementById('learningSummary');
const stageFilter = document.getElementById('stageFilter');

const state = {
  selectedRestaurant: null,
  restaurantMatches: [],
  userLocation: null,
  campaign: null,
  run: null,
  prospects: [],
  dashboard: null,
};

let searchTimer = null;
let searchController = null;
let radiusMap = null;
let radiusMapLayer = null;

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

function updateRadiusDisplay() {
  const miles = Number(radius.value);
  radiusValue.textContent = miles >= 50 ? '50+ mi' : `${miles} mi`;
}

function updateDeliveryRadiusDisplay() {
  deliveryRadiusValue.textContent = `${deliveryRadius.value} mi`;
}

function applyProfileDefaults(match) {
  const type = inferRestaurantType(match);
  supportsCatering.checked = true;
  priceTier.value = 'mid';

  if (['breakfast_brunch', 'cafe_bakery'].includes(type)) {
    primaryDaypart.value = 'breakfast_lunch';
  } else if (['pizza', 'fast_casual'].includes(type)) {
    primaryDaypart.value = 'lunch_dinner';
  } else {
    primaryDaypart.value = 'all_day';
  }

  deliveryRadius.value = String(Math.max(1, Math.min(10, Number(radius.value) || 5)));
  updateDeliveryRadiusDisplay();
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

function requestLiveLocation() {
  if (!navigator.geolocation) {
    locationIndicator.textContent = 'Location unavailable';
    locationIndicator.classList.add('off');
    return;
  }

  navigator.geolocation.getCurrentPosition(
    position => {
      state.userLocation = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      };
      locationIndicator.textContent = 'Near you';
      locationIndicator.classList.add('active');
      locationIndicator.classList.remove('off');

      if (restaurantSearch.value.trim().length >= 2) {
        scheduleRestaurantSearch();
      }
    },
    () => {
      state.userLocation = null;
      locationIndicator.textContent = 'Location off';
      locationIndicator.classList.add('off');
      locationIndicator.classList.remove('active');
    },
    {
      enableHighAccuracy: false,
      timeout: 7000,
      maximumAge: 300000,
    }
  );
}

function renderRestaurantMatches(matches) {
  state.restaurantMatches = matches;

  if (!matches.length) {
    restaurantSearchResults.hidden = false;
    restaurantSearchResults.innerHTML =
      '<div class="location-empty">No nearby restaurant matches yet. Keep typing or add a city/neighborhood.</div>';
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
  state.campaign = null;
  state.run = null;
  state.dashboard = null;
  restaurantSearch.value = match.name;
  selectedRestaurantName.textContent = match.name;
  selectedRestaurantAddress.textContent = match.address;
  selectedRestaurant.hidden = false;
  restaurantSearchResults.hidden = true;
  applyProfileDefaults(match);
  setStatus(`Selected ${match.name}. Choose a radius and run discovery.`, 'success');
}

async function runRestaurantSearch() {
  const query = restaurantSearch.value.trim();

  if (query.length < 2) {
    state.restaurantMatches = [];
    restaurantSearchResults.hidden = true;
    return;
  }

  if (searchController) searchController.abort();
  searchController = new AbortController();

  restaurantSearchResults.hidden = false;
  restaurantSearchResults.innerHTML = '<div class="location-empty">Searching nearby locations…</div>';

  const params = new URLSearchParams({ q: query });
  if (state.userLocation) {
    params.set('lat', state.userLocation.latitude);
    params.set('lon', state.userLocation.longitude);
  }

  try {
    const matches = await request(`/api/restaurants/search?${params.toString()}`, {
      signal: searchController.signal,
    });
    renderRestaurantMatches(matches);
  } catch (error) {
    if (error.name === 'AbortError') return;
    restaurantSearchResults.innerHTML =
      `<div class="location-empty error-text">${escapeHtml(error.message || 'Restaurant search failed.')}</div>`;
  }
}

function scheduleRestaurantSearch() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(runRestaurantSearch, 320);
}

function scoreParts(value) {
  const parts = {};
  String(value || '')
    .split(',')
    .map(part => part.trim())
    .filter(Boolean)
    .forEach(part => {
      const [key, rawScore] = part.split('=');
      parts[key] = Number(rawScore || 0);
    });
  return parts;
}

function parseScoreExplanation(value) {
  return Object.entries(scoreParts(value))
    .map(([key, score]) => `<span>${escapeHtml(humanize(key))} ${escapeHtml(score)}</span>`)
    .join('');
}


function pipelineStage(value) {
  return String(value || 'NEW').toUpperCase();
}

function pipelineLabel(value) {
  return humanize(pipelineStage(value));
}

function dateInputValue(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toISOString().slice(0, 10);
}

function isFollowUpDue(prospect) {
  if (!prospect.next_follow_up_at) return false;
  const stage = pipelineStage(prospect.pipeline_stage);
  if (['WON', 'LOST'].includes(stage)) return false;
  return new Date(prospect.next_follow_up_at).getTime() <= Date.now();
}

function stageOptions(selected) {
  const stages = [
    ['NEW', 'New'],
    ['REVIEWED', 'Reviewed'],
    ['CONTACTED', 'Contacted'],
    ['FOLLOW_UP', 'Follow up'],
    ['REPLIED', 'Replied'],
    ['WON', 'Won'],
    ['LOST', 'Lost'],
  ];
  const current = pipelineStage(selected);
  return stages.map(([value, label]) =>
    `<option value="${value}" ${value === current ? 'selected' : ''}>${label}</option>`
  ).join('');
}

function prospectCard(prospect) {
  const website = safeHttpUrl(prospect.website);
  const source = safeHttpUrl(prospect.source_url);
  const phone = prospect.phone ? String(prospect.phone).trim() : '';
  const phoneHref = phone ? `tel:${phone.replace(/[^+\d]/g, '')}` : null;

  const evidenceSource = safeHttpUrl(prospect.evidence_source_url);
  const evidenceSummary = prospect.evidence_summary
    ? `<div class="evidence-note"><strong>Public evidence</strong><span>${escapeHtml(prospect.evidence_summary)}</span>${evidenceSource ? `<a href="${escapeHtml(evidenceSource)}" target="_blank" rel="noreferrer">source ↗</a>` : ''}</div>`
    : '';
  const stage = pipelineStage(prospect.pipeline_stage);
  const followUpDue = isFollowUpDue(prospect);
  const followUpValue = dateInputValue(prospect.next_follow_up_at);
  const learning = Number(prospect.learned_adjustment || 0);

  const actions = [
    `<button class="action-link email-action" type="button" data-email-prospect="${escapeHtml(prospect.id)}">Email lead</button>`,
    website ? `<a class="action-link" href="${escapeHtml(website)}" target="_blank" rel="noreferrer">Website ↗</a>` : '',
    phoneHref ? `<a class="action-link" href="${escapeHtml(phoneHref)}">Call</a>` : '',
    source ? `<a class="action-link" href="${escapeHtml(source)}" target="_blank" rel="noreferrer">Evidence ↗</a>` : '',
  ].join('');

  return `
    <article class="prospect-card" data-prospect-card="${escapeHtml(prospect.id)}">
      <div class="score-ring"><div><strong>${prospect.score}</strong><span>/100</span></div></div>
      <div class="prospect-main">
        <div class="prospect-topline">
          <span class="category-chip">${escapeHtml(humanize(prospect.category))}</span>
          <span class="signal-chip">${escapeHtml(Number(prospect.distance_miles).toFixed(1))} mi away</span>
          ${prospect.source_name ? `<span class="signal-chip">${escapeHtml(prospect.source_name)}</span>` : ''}
          ${Number(prospect.profile_fit_score || 0) > 0 ? `<span class="signal-chip evidence-chip">Fit +${escapeHtml(prospect.profile_fit_score)}</span>` : ''}
          ${Number(prospect.evidence_score || 0) > 0 ? `<span class="signal-chip evidence-chip">Evidence +${escapeHtml(prospect.evidence_score)}</span>` : ''}
          ${learning !== 0 ? `<span class="signal-chip learning-chip">Learning ${learning > 0 ? '+' : ''}${learning}</span>` : ''}
          <span class="signal-chip stage-chip stage-${stage.toLowerCase()}">${escapeHtml(pipelineLabel(stage))}</span>
          ${followUpDue ? '<span class="signal-chip due-chip">Follow-up due</span>' : ''}
        </div>
        <h3>${escapeHtml(prospect.organization)}</h3>
        <div class="prospect-meta">
          <span>${escapeHtml(prospect.address || 'Address unavailable')}</span>
          ${phone ? `<span>${escapeHtml(phone)}</span>` : ''}
        </div>
        <div class="score-explanation">${parseScoreExplanation(prospect.score_explanation)}</div>
        ${evidenceSummary}
        <details class="pipeline-editor">
          <summary>
            <span>Sales pipeline</span>
            <span>${followUpValue ? `Follow up ${escapeHtml(followUpValue)}` : 'No follow-up scheduled'}</span>
          </summary>
          <div class="pipeline-editor-body">
            <label>
              <span>Stage</span>
              <select data-pipeline-stage="${escapeHtml(prospect.id)}">${stageOptions(stage)}</select>
            </label>
            <label>
              <span>Next follow-up</span>
              <input data-follow-up-date="${escapeHtml(prospect.id)}" type="date" value="${escapeHtml(followUpValue)}" />
            </label>
            <label class="pipeline-note-field">
              <span>Note</span>
              <textarea data-pipeline-note="${escapeHtml(prospect.id)}" rows="2" placeholder="What happened?">${escapeHtml(prospect.pipeline_note || '')}</textarea>
            </label>
            <button class="secondary-button compact" type="button" data-save-pipeline="${escapeHtml(prospect.id)}">Save</button>
          </div>
        </details>
        <details class="why-lead" data-insight-prospect="${escapeHtml(prospect.id)}">
          <summary>
            <span>Why Gather chose this</span>
            <span class="why-score">${escapeHtml(prospect.score)}/100</span>
          </summary>
          <div class="why-body" data-insight-body="${escapeHtml(prospect.id)}">
            <p class="why-note">Open this section to generate a grounded, prospect-specific analysis.</p>
          </div>
        </details>
        <div class="outreach-feedback" data-outreach-feedback="${escapeHtml(prospect.id)}" hidden></div>
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
  const pipelineFilter = stageFilter.value;
  if (pipelineFilter === 'DUE') {
    visible = visible.filter(isFollowUpDue);
  } else if (pipelineFilter !== 'ALL') {
    visible = visible.filter(p => pipelineStage(p.pipeline_stage) === pipelineFilter);
  }

  const sort = sortBy.value;
  visible = [...visible].sort((a, b) => {
    if (sort === 'followup') {
      const aTime = a.next_follow_up_at ? new Date(a.next_follow_up_at).getTime() : Number.MAX_SAFE_INTEGER;
      const bTime = b.next_follow_up_at ? new Date(b.next_follow_up_at).getTime() : Number.MAX_SAFE_INTEGER;
      return aTime - bTime;
    }
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
  mapSection.hidden = false;
  renderMetrics();
  renderProspects();
  renderRadiusMap();
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitForRun(runId) {
  for (let attempt = 0; attempt < 35; attempt += 1) {
    const run = await request(`/api/runs/${encodeURIComponent(runId)}`);
    state.run = run;
    setRunBadge(run.status);

    if (run.status === 'COMPLETED') return run;
    if (run.status === 'FAILED') throw new Error(run.error_message || 'Discovery run failed.');

    if (attempt < 5) {
      setStatus('Scanning nearby organizations with the fast discovery source…');
    } else if (attempt < 15) {
      setStatus('Still scanning. A secondary source may be filling coverage…');
    } else {
      setStatus('Discovery is taking unusually long. It will fail fast instead of waiting indefinitely.');
    }
    await delay(1000);
  }

  throw new Error('Discovery timed out after about 35 seconds. Please run it again.');
}

async function handleCampaignSubmit(event) {
  event.preventDefault();
  const restaurant = state.selectedRestaurant;
  if (!restaurant) {
    setStatus('Choose a restaurant from the live suggestions first.', 'error');
    restaurantSearch.focus();
    return;
  }

  if (state.campaign
      && state.campaign.name === restaurant.name
      && state.campaign.address === restaurant.address) {
    await refreshCurrentCampaign();
    return;
  }

  const restaurantType = inferRestaurantType(restaurant);
  const radiusMiles = Number(radius.value);
  const radiusMeters = Math.round(radiusMiles * 1609.344);

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
        supports_catering: supportsCatering.checked,
        primary_daypart: primaryDaypart.value,
        price_tier: priceTier.value,
        delivery_radius_miles: Number(deliveryRadius.value),
      }),
    });

    state.campaign = campaign;
    resultsTitle.textContent = `Radius prospects for ${campaign.name}`;
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
    watchEnabled.checked = Boolean(campaign.monitoring_enabled);
    watchInterval.value = String(campaign.refresh_interval_days || 7);
    await loadDashboard();
    await loadWorkspaces();
    enrichTopProspects();
  } catch (error) {
    setRunBadge('FAILED');
    setStatus(error.message || 'Something went wrong.', 'error');
  } finally {
    runButton.disabled = false;
    runButton.querySelector('span:first-child').textContent = 'Find prospects';
  }
}

restaurantSearchForm.addEventListener('submit', event => event.preventDefault());

restaurantSearch.addEventListener('input', () => {
  if (state.selectedRestaurant && restaurantSearch.value.trim() !== state.selectedRestaurant.name) {
    state.selectedRestaurant = null;
    selectedRestaurant.hidden = true;
  }
  scheduleRestaurantSearch();
});

restaurantSearch.addEventListener('focus', () => {
  if (state.restaurantMatches.length && !state.selectedRestaurant) {
    restaurantSearchResults.hidden = false;
  }
});

restaurantSearchResults.addEventListener('click', event => {
  const option = event.target.closest('[data-index]');
  if (option) selectRestaurant(Number(option.dataset.index));
});

document.addEventListener('click', event => {
  if (!restaurantSearchForm.contains(event.target) && !restaurantSearchResults.contains(event.target)) {
    restaurantSearchResults.hidden = true;
  }
});

changeRestaurant.addEventListener('click', () => {
  state.selectedRestaurant = null;
  state.campaign = null;
  state.run = null;
  state.dashboard = null;
  selectedRestaurant.hidden = true;
  restaurantSearch.value = '';
  restaurantSearchResults.hidden = true;
  restaurantSearch.focus();
  setStatus('Start typing another restaurant.');
});

campaignForm.addEventListener('submit', handleCampaignSubmit);
radius.addEventListener('input', updateRadiusDisplay);
deliveryRadius.addEventListener('input', updateDeliveryRadiusDisplay);
minScore.addEventListener('input', () => {
  renderProspects();
  renderRadiusMap();
});
sortBy.addEventListener('change', renderProspects);
stageFilter.addEventListener('change', () => {
  renderProspects();
  renderRadiusMap();
});
refreshRadiusButton.addEventListener('click', refreshCurrentCampaign);
watchEnabled.addEventListener('change', saveWatchSettings);
watchInterval.addEventListener('change', saveWatchSettings);
workspaceList.addEventListener('click', event => {
  const button = event.target.closest('[data-workspace-id]');
  if (!button) return;
  const campaign = (state.workspaces || []).find(item => item.id === button.dataset.workspaceId);
  if (campaign) openWorkspace(campaign);
});
salesLoop.addEventListener('click', event => {
  const filter = event.target.closest('[data-stage-filter]');
  if (!filter) return;
  stageFilter.value = filter.dataset.stageFilter;
  renderProspects();
  renderRadiusMap();
});

updateRadiusDisplay();
updateDeliveryRadiusDisplay();
requestLiveLocation();
loadAiStatus();
loadWorkspaces();


async function loadDashboard() {
  if (!state.campaign) return;
  try {
    const dashboard = await request(`/api/campaigns/${encodeURIComponent(state.campaign.id)}/dashboard`);
    state.dashboard = dashboard;
    salesLoop.hidden = false;
    pipelineDue.textContent = dashboard.due_today_count || 0;
    pipelineNew.textContent = dashboard.new_this_week_count || dashboard.new_count || 0;
    pipelineContacted.textContent = dashboard.contacted_count || 0;
    pipelineReplied.textContent = dashboard.replied_count || 0;
    pipelineWon.textContent = dashboard.won_count || 0;

    const learned = dashboard.learned_category_adjustments || {};
    const entries = Object.entries(learned);
    learningSummary.textContent = entries.length
      ? 'Gather learned: ' + entries.map(([category, adjustment]) =>
          `${humanize(category)} ${Number(adjustment) > 0 ? '+' : ''}${adjustment}`
        ).join(' · ') + ' on the next Radius refresh.'
      : 'Gather will learn from replies, wins, and losses as you work this territory.';
  } catch {
    salesLoop.hidden = false;
  }
}

function renderWorkspaces(campaigns) {
  if (!campaigns.length) {
    workspaceList.innerHTML = '<span class="workspace-empty">No saved workspaces yet.</span>';
    return;
  }

  const sorted = [...campaigns].sort((a, b) =>
    new Date(b.last_refresh_at || b.created_at) - new Date(a.last_refresh_at || a.created_at)
  );

  workspaceList.innerHTML = sorted.slice(0, 6).map(campaign => `
    <button class="workspace-item" type="button" data-workspace-id="${escapeHtml(campaign.id)}">
      <span>
        <strong>${escapeHtml(campaign.name)}</strong>
        <small>${escapeHtml(campaign.address)}</small>
      </span>
      <em>${campaign.monitoring_enabled ? 'WATCHING' : 'OPEN'}</em>
    </button>
  `).join('');
}

async function loadWorkspaces() {
  try {
    const campaigns = await request('/api/campaigns');
    state.workspaces = campaigns;
    renderWorkspaces(campaigns);
  } catch {
    workspaceList.innerHTML = '<span class="workspace-empty">Unable to load saved workspaces.</span>';
  }
}

async function openWorkspace(campaign) {
  state.campaign = campaign;
  state.run = null;
  state.selectedRestaurant = {
    name: campaign.name,
    address: campaign.address,
    latitude: campaign.latitude,
    longitude: campaign.longitude,
    category: campaign.business_type,
  };

  restaurantSearch.value = campaign.name;
  selectedRestaurantName.textContent = campaign.name;
  selectedRestaurantAddress.textContent = campaign.address;
  selectedRestaurant.hidden = false;

  radius.value = String(Math.min(50, Math.round(Number(campaign.radius_meters) / 1609.344)));
  updateRadiusDisplay();
  supportsCatering.checked = Boolean(campaign.supports_catering);
  primaryDaypart.value = campaign.primary_daypart || 'all_day';
  priceTier.value = campaign.price_tier || 'mid';
  deliveryRadius.value = String(campaign.delivery_radius_miles || 5);
  updateDeliveryRadiusDisplay();

  watchEnabled.checked = Boolean(campaign.monitoring_enabled);
  watchInterval.value = String(campaign.refresh_interval_days || 7);

  state.prospects = await request(`/api/campaigns/${encodeURIComponent(campaign.id)}/prospects`);
  resultsTitle.textContent = `${campaign.name} sales workspace`;
  resultsSubtitle.textContent =
    `${campaign.address} · persistent Radius territory · ${state.prospects.length} known prospects`;
  setRunBadge('COMPLETED');
  revealResults();
  await loadDashboard();
  setStatus(`Opened ${campaign.name}. Continue the pipeline or refresh Radius for new opportunities.`, 'success');
}

async function refreshCurrentCampaign() {
  if (!state.campaign || refreshRadiusButton.disabled) return;

  refreshRadiusButton.disabled = true;
  refreshRadiusButton.textContent = 'Refreshing…';
  setStatus('Refreshing this Radius while preserving pipeline history…');
  setRunBadge('QUEUED');

  try {
    const run = await request(
      `/api/campaigns/${encodeURIComponent(state.campaign.id)}/runs`,
      { method: 'POST' }
    );
    state.run = run;
    await waitForRun(run.id);
    state.prospects = await request(`/api/runs/${encodeURIComponent(run.id)}/prospects`);
    revealResults();
    await enrichTopProspects();
    await loadDashboard();
    await loadWorkspaces();
  } catch (error) {
    setRunBadge('FAILED');
    setStatus(error.message || 'Radius refresh failed.', 'error');
  } finally {
    refreshRadiusButton.disabled = false;
    refreshRadiusButton.textContent = 'Refresh Radius';
  }
}

async function saveWatchSettings() {
  if (!state.campaign) return;
  try {
    const campaign = await request(
      `/api/campaigns/${encodeURIComponent(state.campaign.id)}/monitoring`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          enabled: watchEnabled.checked,
          interval_days: Number(watchInterval.value),
        }),
      }
    );
    state.campaign = campaign;
    setStatus(
      watchEnabled.checked
        ? `Radius Watch is on. Gather will refresh this territory every ${campaign.refresh_interval_days} day(s).`
        : 'Radius Watch is off. You can still refresh manually.',
      'success'
    );
    await loadWorkspaces();
  } catch (error) {
    setStatus(error.message || 'Unable to update Radius Watch.', 'error');
  }
}

async function saveProspectPipeline(prospectId) {
  const card = prospectList.querySelector(`[data-prospect-card="${CSS.escape(prospectId)}"]`);
  if (!card) return;

  const stage = card.querySelector(`[data-pipeline-stage="${CSS.escape(prospectId)}"]`)?.value || 'NEW';
  const dateValue = card.querySelector(`[data-follow-up-date="${CSS.escape(prospectId)}"]`)?.value || '';
  const note = card.querySelector(`[data-pipeline-note="${CSS.escape(prospectId)}"]`)?.value || '';
  const button = card.querySelector(`[data-save-pipeline="${CSS.escape(prospectId)}"]`);

  if (button) {
    button.disabled = true;
    button.textContent = 'Saving…';
  }

  try {
    const updated = await request(
      `/api/discovered-prospects/${encodeURIComponent(prospectId)}/pipeline`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stage,
          next_follow_up_at: dateValue ? new Date(`${dateValue}T12:00:00`).toISOString() : null,
          note,
        }),
      }
    );

    state.prospects = state.prospects.map(prospect =>
      prospect.id === prospectId ? updated : prospect
    );
    renderProspects();
    renderRadiusMap();
    await loadDashboard();
  } catch (error) {
    setStatus(error.message || 'Unable to update prospect pipeline.', 'error');
  }
}

async function markContactedAfterOutreach(prospectId) {
  const prospect = state.prospects.find(item => item.id === prospectId);
  if (!prospect) return;

  const currentStage = pipelineStage(prospect.pipeline_stage);
  if (['REPLIED', 'WON', 'LOST'].includes(currentStage)) return;

  const followUp = prospect.next_follow_up_at
    ? prospect.next_follow_up_at
    : new Date(Date.now() + (3 * 24 * 60 * 60 * 1000)).toISOString();

  try {
    const updated = await request(
      `/api/discovered-prospects/${encodeURIComponent(prospectId)}/pipeline`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stage: 'CONTACTED',
          next_follow_up_at: followUp,
          note: prospect.pipeline_note || '',
        }),
      }
    );
    state.prospects = state.prospects.map(item => item.id === prospectId ? updated : item);
    await loadDashboard();
  } catch {
    // Outreach itself should still work if pipeline persistence fails.
  }
}


function validCoordinate(value, min, max) {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max;
}

function renderRadiusMap() {
  if (!window.L || !state.campaign || mapSection.hidden) return;

  if (!radiusMap) {
    radiusMap = L.map('radiusMap', {
      zoomControl: true,
      scrollWheelZoom: false,
    });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(radiusMap);
    radiusMapLayer = L.layerGroup().addTo(radiusMap);
  }

  radiusMapLayer.clearLayers();

  const points = [];
  const restaurantLat = Number(state.campaign.latitude);
  const restaurantLon = Number(state.campaign.longitude);

  if (validCoordinate(restaurantLat, -90, 90) && validCoordinate(restaurantLon, -180, 180)) {
    const restaurantMarker = L.circleMarker([restaurantLat, restaurantLon], {
      radius: 10,
      weight: 3,
      color: '#71f2c2',
      fillColor: '#071019',
      fillOpacity: 1,
    }).bindPopup(`<strong>${escapeHtml(state.campaign.name)}</strong><br>Your restaurant`);
    restaurantMarker.addTo(radiusMapLayer);
    points.push([restaurantLat, restaurantLon]);
  }

  const threshold = Number(minScore.value);
  const visible = state.prospects.filter(prospect => Number(prospect.score) >= threshold);

  visible.forEach(prospect => {
    const lat = Number(prospect.latitude);
    const lon = Number(prospect.longitude);
    if (!validCoordinate(lat, -90, 90) || !validCoordinate(lon, -180, 180)) return;
    if (lat === 0 && lon === 0) return;

    const score = Number(prospect.score || 0);
    const marker = L.circleMarker([lat, lon], {
      radius: Math.max(6, Math.min(11, 5 + score / 20)),
      weight: 2,
      color: score >= 75 ? '#71f2c2' : '#6db7ff',
      fillColor: '#0f1d28',
      fillOpacity: 0.92,
    }).bindPopup(
      `<strong>${escapeHtml(prospect.organization)}</strong><br>` +
      `${escapeHtml(humanize(prospect.category))} · ${score}/100<br>` +
      `${escapeHtml(Number(prospect.distance_miles).toFixed(1))} mi away`
    );

    marker.on('click', () => {
      const card = prospectList.querySelector(`[data-prospect-card="${CSS.escape(prospect.id)}"]`);
      if (card) card.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    marker.addTo(radiusMapLayer);
    points.push([lat, lon]);
  });

  if (points.length > 1) {
    radiusMap.fitBounds(points, { padding: [28, 28], maxZoom: 13 });
  } else if (points.length === 1) {
    radiusMap.setView(points[0], 11);
  }

  mapStatus.textContent = `${Math.max(0, points.length - 1)} mapped prospects`;
  setTimeout(() => radiusMap.invalidateSize(), 0);
}

async function enrichTopProspects() {
  if (!state.run || !state.prospects.length) return;

  const targets = [...state.prospects]
    .sort((a, b) => Number(b.score) - Number(a.score))
    .slice(0, 10);

  setStatus(`Found ${state.prospects.length} prospects. Refining the top ${targets.length} with restaurant fit and public evidence…`);

  await Promise.allSettled(
    targets.map(prospect => request(
      `/api/discovered-prospects/${encodeURIComponent(prospect.id)}/evidence`,
      { method: 'POST' }
    ))
  );

  try {
    state.prospects = await request(`/api/runs/${encodeURIComponent(state.run.id)}/prospects`);
    renderMetrics();
    renderProspects();
    renderRadiusMap();
    setStatus(
      `Complete. Ranked ${state.prospects.length} prospects and evidence-enriched the strongest candidates.`,
      'success'
    );
  } catch {
    setStatus(
      `Complete. Found ${state.prospects.length} prospects; some evidence enrichment could not be refreshed.`,
      'success'
    );
  }
}


async function loadAiStatus() {
  try {
    const status = await request('/api/ai/status');
    if (status.enabled) {
      aiStatusPill.textContent = 'AI ON';
      aiStatusPill.classList.remove('off');
      aiStatusText.textContent = `Generated analysis · ${status.model}`;
    } else {
      aiStatusPill.textContent = 'AI OFF';
      aiStatusPill.classList.add('off');
      aiStatusText.textContent = 'Heuristic fallback · add API key';
    }
  } catch {
    aiStatusPill.textContent = 'AI ?';
    aiStatusPill.classList.add('off');
    aiStatusText.textContent = 'AI status unavailable';
  }
}

async function loadProspectInsight(details) {
  if (!details || details.dataset.loaded === 'true' || details.dataset.loading === 'true') return;

  const prospectId = details.dataset.insightProspect;
  const body = details.querySelector('[data-insight-body]');
  if (!prospectId || !body) return;

  details.dataset.loading = 'true';
  body.innerHTML = '<p class="why-note">Gather is analyzing this prospect against the available evidence…</p>';

  try {
    const insight = await request(
      `/api/discovered-prospects/${encodeURIComponent(prospectId)}/insight`,
      { method: 'POST' }
    );

    const generatorLabel = insight.generative_ai
      ? `Generated with ${escapeHtml(insight.generator)}`
      : 'Heuristic fallback';

    const reasons = (insight.reasons || [])
      .map(reason => `<li>${escapeHtml(reason)}</li>`)
      .join('');

    body.innerHTML = `
      <div class="insight-meta">
        <span class="insight-generator ${insight.generative_ai ? 'active' : 'fallback'}">${generatorLabel}</span>
      </div>
      <ul>${reasons}</ul>
      <p class="why-note">${escapeHtml(insight.evidence_note || '')}</p>
    `;

    details.dataset.loaded = 'true';
  } catch (error) {
    body.innerHTML = `<p class="why-note error-text">${escapeHtml(error.message || 'Unable to generate prospect analysis.')}</p>`;
  } finally {
    details.dataset.loading = 'false';
  }
}

async function copyText(value) {
  if (!navigator.clipboard) return false;
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    return false;
  }
}

prospectList.addEventListener('click', async event => {
  const savePipeline = event.target.closest('[data-save-pipeline]');
  if (savePipeline) {
    await saveProspectPipeline(savePipeline.dataset.savePipeline);
    return;
  }

  const summary = event.target.closest('details[data-insight-prospect] > summary');
  if (summary) {
    const details = summary.parentElement;
    setTimeout(() => {
      if (details.open) loadProspectInsight(details);
    }, 0);
    return;
  }

  const button = event.target.closest('[data-email-prospect]');
  if (!button) return;

  const prospectId = button.dataset.emailProspect;
  const feedback = prospectList.querySelector(`[data-outreach-feedback="${CSS.escape(prospectId)}"]`);
  const originalText = button.textContent;

  button.disabled = true;
  button.textContent = 'Finding email…';
  if (feedback) {
    feedback.hidden = false;
    feedback.className = 'outreach-feedback';
    feedback.textContent = 'Checking source tags, the official site, and contact pages for a public email…';
  }

  try {
    const outreach = await request(
      `/api/discovered-prospects/${encodeURIComponent(prospectId)}/outreach`,
      { method: 'POST' }
    );

    if (outreach.email) {
      const mailto = new URL(`mailto:${outreach.email}`);
      mailto.searchParams.set('subject', outreach.subject);
      mailto.searchParams.set('body', outreach.body);

      if (feedback) {
        feedback.className = 'outreach-feedback success';
        const draftLabel = outreach.generative_ai
          ? `AI-personalized with ${escapeHtml(outreach.generator)}`
          : 'heuristic draft';
        feedback.innerHTML =
          `Found <strong>${escapeHtml(outreach.email)}</strong> from a public source · ${draftLabel}. Opening your email app…`;
      }
      await markContactedAfterOutreach(prospectId);
      window.location.href = mailto.toString();
    } else {
      const copied = await copyText(`${outreach.subject}\n\n${outreach.body}`);
      if (feedback) {
        feedback.className = 'outreach-feedback warning';
        feedback.textContent = copied
          ? 'No public email found. The suggested outreach draft was copied to your clipboard.'
          : 'No public email found. Use the Website action to find the organization’s preferred contact route.';
      }
    }
  } catch (error) {
    if (feedback) {
      feedback.className = 'outreach-feedback error';
      feedback.textContent = error.message || 'Unable to prepare outreach.';
    }
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
});
