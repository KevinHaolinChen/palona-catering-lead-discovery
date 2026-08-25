const container = document.getElementById('prospects');
const summary = document.getElementById('summary');
const slider = document.getElementById('minScore');
const scoreValue = document.getElementById('scoreValue');
let allProspects = [];

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function evidenceItem(e) {
  const source = e.source_url
    ? `<a href="${escapeHtml(e.source_url)}" target="_blank" rel="noreferrer">source ↗</a>`
    : '<span>no external source</span>';
  return `
    <li>
      <div class="evidence-head">
        <span class="badge ${e.kind === 'ai_generated' ? 'ai' : e.kind}">${escapeHtml(e.kind.replace('_', ' ').toUpperCase())}</span>
        <span class="confidence">${Math.round(e.confidence * 100)}% confidence</span>
      </div>
      <p>${escapeHtml(e.text)}</p>
      <div class="source-line">${source}</div>
    </li>`;
}

function scoreBars(p) {
  const labels = [
    ['Proximity', p.score_breakdown.proximity, 25],
    ['Scale', p.score_breakdown.organization_scale, 20],
    ['Events', p.score_breakdown.event_meeting_signal, 25],
    ['Food need', p.score_breakdown.food_need_signal, 20],
    ['Contact', p.score_breakdown.contactability, 10],
  ];
  return labels.map(([label, value, max]) => `
    <div class="score-row">
      <span>${label}</span>
      <div class="bar"><div style="width:${(value / max) * 100}%"></div></div>
      <strong>${value}/${max}</strong>
    </div>`).join('');
}

function prospectCard(p) {
  const uncertainty = p.uncertainties.length
    ? `<div class="uncertainty"><strong>Uncertainty</strong>${p.uncertainties.map(x => `<p>${escapeHtml(x)}</p>`).join('')}</div>`
    : '';
  return `
    <article class="card">
      <div class="card-top">
        <div>
          <div class="category">${escapeHtml(p.category.replaceAll('_', ' '))}</div>
          <h2>${escapeHtml(p.organization)}</h2>
          <p class="address">${escapeHtml(p.address)} · ${escapeHtml(p.proximity_band)}</p>
        </div>
        <div class="score">${p.score}<span>/100</span></div>
      </div>
      <p class="fit">${escapeHtml(p.fit_summary)}</p>
      <details>
        <summary>Why this score</summary>
        <div class="score-breakdown">${scoreBars(p)}</div>
      </details>
      <details>
        <summary>Evidence & provenance (${p.evidence.length})</summary>
        <ul class="evidence">${p.evidence.map(evidenceItem).join('')}</ul>
      </details>
      <div class="contact">
        <div><span>Suggested role</span><strong>${escapeHtml(p.contact.role)}</strong></div>
        <div><span>Public contact path</span><strong>${escapeHtml(p.contact.channel)}${p.contact.value ? ` · ${escapeHtml(p.contact.value)}` : ''}</strong></div>
        ${p.contact.source_url ? `<a href="${escapeHtml(p.contact.source_url)}" target="_blank" rel="noreferrer">inspect contact source ↗</a>` : ''}
      </div>
      ${uncertainty}
      <div class="outreach">
        <div class="outreach-title"><span class="badge ai">GENERATED</span><strong>Outreach draft</strong></div>
        <p>${escapeHtml(p.outreach_draft)}</p>
        <small>${escapeHtml(p.outreach_generated_by)}</small>
      </div>
    </article>`;
}

function render() {
  const minScore = Number(slider.value);
  scoreValue.textContent = minScore;
  const visible = allProspects.filter(p => p.score >= minScore);
  summary.innerHTML = `<strong>${visible.length}</strong> prospects shown · <strong>${allProspects.length}</strong> cached and reviewable without network access`;
  container.innerHTML = visible.map(prospectCard).join('') || '<p>No prospects match this score.</p>';
}

async function load() {
  const response = await fetch('/api/prospects');
  allProspects = await response.json();
  render();
}

slider.addEventListener('input', render);
load().catch(err => {
  container.innerHTML = `<p>Failed to load prospects: ${escapeHtml(err.message)}</p>`;
});
