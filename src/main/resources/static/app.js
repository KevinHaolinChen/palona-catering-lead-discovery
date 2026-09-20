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

const state = {
  selectedRestaurant: null,
  restaurantMatches: [],
  userLocation: null,
  campaign: null,
  run: null,
  prospects: [],
};

let searchTimer = null;
let searchController = null;

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
  restaurantSearch.value = match.name;
  selectedRestaurantName.textContent = match.name;
  selectedRestaurantAddress.textContent = match.address;
  selectedRestaurant.hidden = false;
  restaurantSearchResults.hidden = true;
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

function leadReasons(prospect) {
  const parts = scoreParts(prospect.score_explanation);
  const distance = Number(prospect.distance_miles);
  const reasons = [];

  if (distance <= 2) {
    reasons.push(`Very close to your restaurant at ${distance.toFixed(1)} miles, which can make delivery and repeat orders easier.`);
  } else if (distance <= 5) {
    reasons.push(`Within a practical local delivery range at ${distance.toFixed(1)} miles.`);
  } else {
    reasons.push(`Inside your selected search radius at ${distance.toFixed(1)} miles.`);
  }

  const categoryReason = {
    corporate_office: 'Corporate offices can create recurring group-food occasions such as team meetings, visitor days, and employee meals.',
    corporate_hq: 'A headquarters can have recurring workplace, visitor, and meeting-related group-food demand.',
    corporate_campus: 'A larger corporate campus can create multiple workplace and event-related group-order opportunities.',
    university_campus: 'Campus departments can have meetings, trainings, student programs, and other group-food occasions.',
    hospital: 'Hospitals have many departments and staff meetings, though outside-vendor rules can reduce conversion likelihood.',
    community_event_space: 'Event and community spaces directly host group gatherings that can create catering demand.',
  }[prospect.category];

  if (categoryReason) reasons.push(categoryReason);
  else reasons.push(`Its ${humanize(prospect.category).toLowerCase()} profile is a plausible local group-order prospect.`);

  if ((parts.contact || 0) >= 7) {
    reasons.push('A public website or phone path is available, making the account easier to contact and verify.');
  } else {
    reasons.push('Public contactability is limited, so this lead may require extra research before outreach.');
  }

  if ((parts.events || 0) >= 17 || (parts.need || 0) >= 17) {
    reasons.push('The current cold-start model gives this category above-average meeting or group-food signals.');
  }

  return reasons;
}

function prospectCard(prospect) {
  const website = safeHttpUrl(prospect.website);
  const source = safeHttpUrl(prospect.source_url);
  const phone = prospect.phone ? String(prospect.phone).trim() : '';
  const phoneHref = phone ? `tel:${phone.replace(/[^+\d]/g, '')}` : null;

  const actions = [
    `<button class="action-link email-action" type="button" data-email-prospect="${escapeHtml(prospect.id)}">Email lead</button>`,
    website ? `<a class="action-link" href="${escapeHtml(website)}" target="_blank" rel="noreferrer">Website ↗</a>` : '',
    phoneHref ? `<a class="action-link" href="${escapeHtml(phoneHref)}">Call</a>` : '',
    source ? `<a class="action-link" href="${escapeHtml(source)}" target="_blank" rel="noreferrer">Evidence ↗</a>` : '',
  ].join('');

  const reasons = leadReasons(prospect)
    .map(reason => `<li>${escapeHtml(reason)}</li>`)
    .join('');

  return `
    <article class="prospect-card" data-prospect-card="${escapeHtml(prospect.id)}">
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
        <details class="why-lead">
          <summary>
            <span>Why VerityScout AI chose this</span>
            <span class="why-score">${escapeHtml(prospect.score)}/100</span>
          </summary>
          <div class="why-body">
            <p class="why-note">Signal-based rationale from the current explainable scoring model.</p>
            <ul>${reasons}</ul>
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

  const restaurantType = inferRestaurantType(restaurant);
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
  selectedRestaurant.hidden = true;
  restaurantSearch.value = '';
  restaurantSearchResults.hidden = true;
  restaurantSearch.focus();
  setStatus('Start typing another restaurant.');
});

campaignForm.addEventListener('submit', handleCampaignSubmit);
minScore.addEventListener('input', renderProspects);
sortBy.addEventListener('change', renderProspects);

requestLiveLocation();


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
    feedback.textContent = 'Checking the organization’s public website for a contact email…';
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
        feedback.innerHTML =
          `Found <strong>${escapeHtml(outreach.email)}</strong> from a public source. Opening your email app…`;
      }
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
