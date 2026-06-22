/* ═══════════════════════════════════════════════════════════
   MEDIRUSH — MAIN SCRIPT v2
   Full role-based logic: ADMIN | PATIENT | DRIVER
═══════════════════════════════════════════════════════════ */
'use strict';

// ── STATE ─────────────────────────────────────────────────
const S = {
  user: null,
  map: null,
  markerPatient: null,
  markerAmb: null,
  markerHospital: null,
  routeLine: null,
  simulTimer: null,
  pollTimer: null,
  lat: null,
  lon: null,
  priority: 'MEDIUM',
  activeReqId: null,
  driverAmbId: null,
};

// ── BOOT ──────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  S.user = JSON.parse(localStorage.getItem('amb_user') || 'null');

  // Only redirect if we're on dashboard
  if (!S.user && document.getElementById('main-content')) {
    window.location.href = 'login.html';
    return;
  }
  if (!S.user) return; // auth pages

  const roleEl = document.getElementById('nav-role');
  const nameEl = document.getElementById('nav-name');
  if (nameEl) nameEl.textContent = S.user.name;
  if (roleEl) { roleEl.textContent = S.user.role; roleEl.className = 'nav-role ' + S.user.role; }

  buildSidebar();
  loadDefault();
});

function logout() {
  clearAll();
  localStorage.removeItem('amb_user');
  window.location.href = 'login.html';
}

function clearAll() {
  if (S.simulTimer) clearInterval(S.simulTimer);
  if (S.pollTimer)  clearInterval(S.pollTimer);
  destroyMap();
}

// ── TOAST ─────────────────────────────────────────────────
function toast(title, body = '', type = 'info') {
  const icons = { success:'✅', error:'❌', warning:'⚠️', info:'ℹ️', panic:'🚨' };
  const wrap = document.getElementById('toast-wrap');
  if (!wrap) return;
  const el = document.createElement('div');
  el.className = `toast t-${type}`;
  el.innerHTML = `<span class="toast-icon">${icons[type]||'ℹ️'}</span>
    <div class="toast-body"><strong>${esc(title)}</strong>${body ? '<div>'+esc(body)+'</div>' : ''}</div>`;
  wrap.appendChild(el);
  setTimeout(() => el.remove(), type==='panic' ? 6000 : 3500);
}

// ── SIDEBAR ───────────────────────────────────────────────
function buildSidebar() {
  const sb = document.getElementById('sidebar');
  if (!sb) return;
  const role = S.user.role;
  const menus = {
    ADMIN: [
      { group:'Dashboard', items:[
        { icon:'📊', label:'Overview',   panel:'admin-overview' },
        { icon:'📡', label:'Activity Logs', panel:'admin-logs' },
      ]},
      { group:'Management', items:[
        { icon:'📋', label:'Bookings',   panel:'admin-bookings' },
        { icon:'🚑', label:'Ambulances', panel:'admin-ambulances' },
        { icon:'🧑‍✈️', label:'Drivers',   panel:'admin-drivers' },
        { icon:'👥', label:'All Users',  panel:'admin-users' },
      ]},
    ],
    PATIENT: [
      { group:'Emergency', items:[
        { icon:'🆘', label:'Book Ambulance', panel:'patient-book' },
        { icon:'📜', label:'My History',     panel:'patient-history' },
      ]},
    ],
    DRIVER: [
      { group:'Operations', items:[
        { icon:'🚑', label:'Driver Console', panel:'driver-console' },
      ]},
    ],
  };

  const groups = menus[role] || [];
  sb.innerHTML = groups.map(g => `
    <div class="sidebar-group-label">${g.group}</div>
    ${g.items.map(i => `
      <button class="sidebar-item" id="nav-${i.panel}" onclick="loadPanel('${i.panel}')">
        <span class="si-icon">${i.icon}</span>${i.label}
      </button>`).join('')}
  `).join('');
}

function setActiveNav(panel) {
  document.querySelectorAll('.sidebar-item').forEach(el => el.classList.remove('active'));
  const el = document.getElementById('nav-' + panel);
  if (el) el.classList.add('active');
}

function loadDefault() {
  const defaults = { ADMIN:'admin-overview', PATIENT:'patient-book', DRIVER:'driver-console' };
  loadPanel(defaults[S.user.role] || 'admin-overview');
}

// ── PANEL LOADER ──────────────────────────────────────────
function loadPanel(panel) {
  clearAll();
  destroyMap();
  setActiveNav(panel);

  const tpl = document.getElementById('tpl-' + panel);
  const main = document.getElementById('main-content');
  if (!main) return;
  if (!tpl) { main.innerHTML = '<p class="text-muted" style="padding:48px">Panel not found.</p>'; return; }

  main.innerHTML = '';
  main.appendChild(tpl.content.cloneNode(true));

  const handlers = {
    'admin-overview':   initAdminOverview,
    'admin-bookings':   initAdminBookings,
    'admin-drivers':    initAdminDrivers,
    'admin-ambulances': initAdminAmbulances,
    'admin-users':      initAdminUsers,
    'admin-logs':       initAdminLogs,
    'patient-book':     initPatientBook,
    'patient-history':  initPatientHistory,
    'driver-console':   initDriverConsole,
  };
  if (handlers[panel]) handlers[panel]();
}

// ═══════════════════════════════════════════════════════════
//  MAP HELPERS
// ═══════════════════════════════════════════════════════════
function initMap(lat = 11.6643, lon = 78.1460, zoom = 13) {
  if (S.map) destroyMap();
  S.map = L.map('map').setView([lat, lon], zoom);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap', maxZoom: 19
  }).addTo(S.map);
  return S.map;
}

function destroyMap() {
  if (S.map) { S.map.remove(); S.map = null; }
  S.markerPatient = S.markerAmb = S.markerHospital = S.routeLine = null;
}

function makeIcon(emoji, color, size = 36) {
  return L.divIcon({
    html: `<div style="background:${color};width:${size}px;height:${size}px;border-radius:50% 50% 50% 0;
      transform:rotate(-45deg);display:flex;align-items:center;justify-content:center;
      border:2.5px solid rgba(255,255,255,.75);box-shadow:0 4px 14px rgba(0,0,0,.6)">
      <span style="transform:rotate(45deg);font-size:${size*.42}px">${emoji}</span></div>`,
    iconSize: [size, size], iconAnchor: [size/2, size], popupAnchor: [0, -size], className: ''
  });
}

function drawRoute(la1, lo1, la2, lo2) {
  if (!S.map) return;
  if (S.routeLine) S.map.removeLayer(S.routeLine);
  S.routeLine = L.polyline([[la1,lo1],[la2,lo2]], {
    color:'#3b82f6', weight:3, opacity:.7, dashArray:'8 5'
  }).addTo(S.map);
}

function placePatient(lat, lon, label = 'Your location') {
  if (!S.map) return;
  if (S.markerPatient) S.map.removeLayer(S.markerPatient);
  S.markerPatient = L.marker([lat,lon], {icon: makeIcon('🏥','#e63946')})
    .addTo(S.map).bindPopup(`<strong>📍 ${label}</strong><br/>(${lat.toFixed(5)}, ${lon.toFixed(5)})`).openPopup();
}

function placeAmb(lat, lon, label = 'Ambulance') {
  if (!S.map) return;
  if (S.markerAmb) S.map.removeLayer(S.markerAmb);
  S.markerAmb = L.marker([lat,lon], {icon: makeIcon('🚑','#2563eb')})
    .addTo(S.map).bindPopup(`<strong>🚑 ${label}</strong>`);
}

function placeHospital(lat, lon, name) {
  if (!S.map) return;
  if (S.markerHospital) S.map.removeLayer(S.markerHospital);
  S.markerHospital = L.marker([lat,lon], {icon: makeIcon('🏨','#22c55e', 30)})
    .addTo(S.map).bindPopup(`<strong>🏥 ${name}</strong>`);
}

// Simulate ambulance moving toward patient
function startSimulation(ambLat, ambLon, patLat, patLon, ambId) {
  if (S.simulTimer) clearInterval(S.simulTimer);
  let lat = ambLat, lon = ambLon;
  const steps = 50, dLat = (patLat - ambLat)/steps, dLon = (patLon - ambLon)/steps;
  let step = 0;
  S.simulTimer = setInterval(() => {
    if (!S.map || step >= steps) { clearInterval(S.simulTimer); return; }
    step++;
    lat += dLat + (Math.random()-.5)*.0004;
    lon += dLon + (Math.random()-.5)*.0004;
    if (S.markerAmb) S.markerAmb.setLatLng([lat,lon]);
    if (S.routeLine) S.routeLine.setLatLngs([[lat,lon],[patLat,patLon]]);
    if (ambId) {
      fetch(`/api/ambulances/${ambId}/location`, {
        method:'PUT', headers:{'Content-Type':'application/json'},
        body: JSON.stringify({latitude:lat, longitude:lon})
      }).catch(()=>{});
    }
  }, 3000);
}

// ═══════════════════════════════════════════════════════════
//  ADMIN PANELS
// ═══════════════════════════════════════════════════════════
async function initAdminOverview() {
  try {
    const [dash, ambs] = await Promise.all([
      api('/api/admin/dashboard'),
      api('/api/ambulances')
    ]);
    set('st-users',    dash.totalUsers);
    set('st-patients', dash.totalPatients);
    set('st-drivers',  dash.totalDrivers);
    set('st-requests', dash.totalRequests);
    const s = dash.ambulanceStats || {};
    set('st-avail',     s.AVAILABLE || 0);
    set('st-busy',      s.BUSY || 0);
    set('st-offline',   s.OFFLINE || 0);
    set('st-total-amb', s.TOTAL || 0);

    const map = initMap(11.6643, 78.1460, 12);
    ambs.forEach(a => {
      if (!a.latitude) return;
      const color = a.status==='AVAILABLE'?'#22c55e': a.status==='BUSY'?'#e63946':'#4a5468';
      L.marker([a.latitude,a.longitude], {icon: makeIcon('🚑',color)})
        .addTo(map)
        .bindPopup(`<strong>${esc(a.vehicleNumber)}</strong><br/>Driver: ${esc(a.driverName||'—')}<br/>Status: ${a.status}`);
    });

    await loadLogsInto('admin-logs-list');
  } catch(e) { toast('Load error', e.message, 'error'); }
}

async function initAdminBookings() {
  try {
    const rows = await api('/api/emergency/all');
    const tb = document.getElementById('bookings-tbody');
    if (!rows.length) { tb.innerHTML = emptyRow(10, 'No bookings yet'); return; }
    tb.innerHTML = rows.map(r => `<tr>
      <td>${r.id}</td>
      <td><strong>${esc(r.patientName)}</strong><br/><span class="text-sm text-muted">${esc(r.patientPhone)}</span></td>
      <td><span class="badge badge-${r.priority}">${r.priority}</span>${r.panic?'<br/><span style="color:var(--red);font-size:10px">🚨 PANIC</span>':''}</td>
      <td class="mono">${esc(r.vehicleNumber||'—')}</td>
      <td>${esc(r.driverName||'—')}</td>
      <td class="text-sm">${esc(r.hospitalName||'—')}</td>
      <td>${r.distance!=null?r.distance+' km':'—'}</td>
      <td>${r.eta!=null?r.eta+' min':'—'}</td>
      <td><span class="badge badge-${r.status}">${r.status}</span></td>
      <td class="text-sm text-muted">${fmtDate(r.requestedAt)}</td>
    </tr>`).join('');
  } catch(e) { toast('Error','','error'); }
}

async function initAdminDrivers() {
  try {
    const rows = await api('/api/admin/drivers');
    const tb = document.getElementById('drivers-tbody');
    if (!rows.length) { tb.innerHTML = emptyRow(7, 'No drivers'); return; }
    tb.innerHTML = rows.map((d,i) => `<tr>
      <td>${i+1}</td>
      <td><strong>${esc(d.name)}</strong></td>
      <td>${esc(d.email)}</td>
      <td>${esc(d.phone)}</td>
      <td class="mono">${esc(d.vehicleNumber||'—')}</td>
      <td>${d.ambulanceStatus?`<span class="badge badge-${d.ambulanceStatus}">${d.ambulanceStatus}</span>`:'—'}</td>
      <td class="text-sm text-muted">${d.latitude?d.latitude.toFixed(4)+', '+d.longitude.toFixed(4):'—'}</td>
    </tr>`).join('');
  } catch(e) { toast('Error','','error'); }
}

async function initAdminAmbulances() {
  try {
    const rows = await api('/api/ambulances');
    const tb = document.getElementById('amb-tbody');
    if (!rows.length) { tb.innerHTML = emptyRow(7, 'No ambulances'); return; }
    tb.innerHTML = rows.map((a,i) => `<tr>
      <td>${i+1}</td>
      <td class="mono"><strong>${esc(a.vehicleNumber)}</strong></td>
      <td>${esc(a.driverName||'—')}</td>
      <td><span class="badge badge-${a.status}">${a.status}</span></td>
      <td class="mono text-sm">${a.latitude!=null?a.latitude.toFixed(5):'—'}</td>
      <td class="mono text-sm">${a.longitude!=null?a.longitude.toFixed(5):'—'}</td>
      <td class="text-sm text-muted">${fmtDate(a.lastUpdated)}</td>
    </tr>`).join('');
  } catch(e) { toast('Error','','error'); }
}

async function initAdminUsers() {
  try {
    const rows = await api('/api/admin/users');
    const tb = document.getElementById('users-tbody');
    if (!rows.length) { tb.innerHTML = emptyRow(6, 'No users'); return; }
    tb.innerHTML = rows.map((u,i) => `<tr>
      <td>${i+1}</td>
      <td><strong>${esc(u.name)}</strong></td>
      <td>${esc(u.email)}</td>
      <td><span class="badge badge-${u.role.toLowerCase()}">${u.role}</span></td>
      <td>${esc(u.phone||'—')}</td>
      <td class="text-sm text-muted">${fmtDate(u.createdAt)}</td>
    </tr>`).join('');
  } catch(e) { toast('Error','','error'); }
}

async function initAdminLogs() {
  await loadLogsInto('all-logs-list');
}

async function loadLogsInto(elId) {
  const el = document.getElementById(elId);
  if (!el) return;
  try {
    const logs = await api('/api/emergency/logs');
    if (!logs.length) { el.innerHTML = '<div class="text-muted" style="padding:24px;text-align:center">No activity yet.</div>'; return; }
    el.innerHTML = logs.map(l => logItemHtml(l)).join('');
  } catch(e) {
    el.innerHTML = '<div class="alert alert-error">Failed to load logs.</div>';
  }
}

function logItemHtml(l) {
  const icons = { SUCCESS:'✅', INFO:'ℹ️', WARNING:'⚠️', DANGER:'🚨' };
  return `<div class="log-item log-${l.type||'INFO'}">
    <span class="log-icon">${icons[l.type]||'ℹ️'}</span>
    <div class="log-msg">
      <strong>${esc(l.actorName||'System')} [${l.actorRole||''}]${l.requestId?' · Req #'+l.requestId:''}</strong><br/>
      ${esc(l.message)}
    </div>
    <span class="log-time">${fmtDateTime(l.createdAt)}</span>
  </div>`;
}

// ═══════════════════════════════════════════════════════════
//  PATIENT PANELS
// ═══════════════════════════════════════════════════════════
let _selectedPriority = 'MEDIUM';

function initPatientBook() {
  selectPriority('MEDIUM');
  initMap(11.6643, 78.1460, 13);
  S.map.on('click', e => {
    S.lat = e.latlng.lat; S.lon = e.latlng.lng;
    placePatient(S.lat, S.lon, 'Selected on map');
    enableBook();
    toast('Location set', 'Click the map again to change.', 'info');
    showNearestPreview();
  });
  checkExistingRequest();
}

function selectPriority(p) {
  _selectedPriority = p;
  ['HIGH','MEDIUM','LOW'].forEach(x => {
    const el = document.getElementById('popt-' + x);
    if (el) el.className = 'priority-opt' + (x===p ? ' sel-'+x : '');
  });
}

function enableBook() {
  const btn = document.getElementById('book-btn');
  if (btn) btn.disabled = false;
}

async function detectLocation() {
  const btn = document.getElementById('detect-btn');
  btn.disabled = true; btn.innerHTML = '<div class="spinner"></div> Detecting…';

  const done = (lat, lon, label) => {
    S.lat = lat; S.lon = lon;
    placePatient(lat, lon, label);
    if (S.map) S.map.setView([lat, lon], 15);
    const gi = document.getElementById('gps-info');
    const gt = document.getElementById('gps-text');
    if (gi) gi.classList.remove('hidden');
    if (gt) gt.textContent = lat.toFixed(5) + ', ' + lon.toFixed(5);
    enableBook();
    btn.disabled = false; btn.innerHTML = '📡 Auto-Detect GPS';
    toast('Location detected!', label, 'success');
    showNearestPreview();
  };

  if (!navigator.geolocation) {
    done(11.6643, 78.1460, 'Salem, TN (default)');
    return;
  }
  navigator.geolocation.getCurrentPosition(
    pos => done(pos.coords.latitude, pos.coords.longitude, 'GPS detected'),
    ()  => done(11.6643, 78.1460, 'Salem, TN (default — permission denied)'),
    { timeout:8000 }
  );
}

async function showNearestPreview() {
  if (!S.lat || !S.lon) return;
  const sec = document.getElementById('nearest-section');
  const wrap = document.getElementById('amb-cards-wrap');
  if (!sec || !wrap) return;
  sec.classList.remove('hidden');
  wrap.innerHTML = '<div class="loading-block"><div class="spinner"></div> Finding nearest…</div>';

  try {
    const ambs = await api(`/api/ambulances/nearest?lat=${S.lat}&lon=${S.lon}&priority=${_selectedPriority}`);
    if (!ambs.length) {
      wrap.innerHTML = '<div class="alert alert-warning">No ambulances within range currently.</div>';
      return;
    }
    wrap.innerHTML = ambs.map((a, i) => `
      <div class="amb-card">
        <div class="rank">#${i+1}</div>
        <div class="vnum">${esc(a.vehicleNumber)}</div>
        <div class="dname">👤 ${esc(a.driverName||'N/A')}</div>
        <div class="amb-meta">
          <span class="amb-dist">📍 ${a.distance} km</span>
          <span class="amb-eta">⏱ ~${a.eta} min</span>
        </div>
      </div>`).join('');

    // Pin them on map
    ambs.forEach((a, i) => {
      if (a.latitude && S.map) {
        const color = ['#2563eb','#7c3aed','#0891b2'][i] || '#2563eb';
        L.marker([a.latitude, a.longitude], {icon: makeIcon('🚑', color, 30)})
          .addTo(S.map)
          .bindPopup(`<strong>#${i+1} ${esc(a.vehicleNumber)}</strong><br/>${a.distance}km · ${a.eta}min`);
      }
    });
  } catch(e) {
    wrap.innerHTML = '<div class="alert alert-error">Failed to load nearby ambulances.</div>';
  }
}

async function triggerPanic() {
  if (!S.lat || !S.lon) {
    // Try auto-detect first
    toast('🚨 PANIC', 'Detecting location…', 'panic');
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        pos => { S.lat = pos.coords.latitude; S.lon = pos.coords.longitude; _doPanic(); },
        ()  => { S.lat = 11.6643; S.lon = 78.1460; _doPanic(); },
        { timeout: 5000 }
      );
    } else {
      S.lat = 11.6643; S.lon = 78.1460; _doPanic();
    }
    return;
  }
  _doPanic();
}

async function _doPanic() {
  toast('🚨 PANIC ALERT', 'Emergency request sent with HIGH priority!', 'panic');
  selectPriority('HIGH');
  const addr = document.getElementById('p-address');
  const address = addr ? addr.value.trim() || 'PANIC - Auto location' : 'PANIC - Auto location';
  await _sendRequest(S.lat, S.lon, address, 'HIGH', true);
}

async function bookAmbulance() {
  if (!S.lat || !S.lon) { toast('No location', 'Please detect or tap map for location', 'warning'); return; }
  const addr = (document.getElementById('p-address')?.value||'').trim() || 'Detected location';
  await _sendRequest(S.lat, S.lon, addr, _selectedPriority, false);
}

async function _sendRequest(lat, lon, address, priority, panic) {
  const btn = document.getElementById('book-btn');
  if (btn) { btn.disabled = true; btn.innerHTML = '<div class="spinner"></div> Dispatching…'; }

  try {
    const data = await api(`/api/emergency/request/${S.user.id}`, 'POST', {latitude:lat, longitude:lon, address, priority, panic});

    if (data.success) {
      S.activeReqId = data.requestId;
      toast('Dispatched!', `${data.candidateCount||1} driver(s) notified. ETA ~${data.eta} min`, 'success');
      renderBookingResult(data);

      if (data.hospitalLat && S.map) placeHospital(data.hospitalLat, data.hospitalLon, data.hospitalName);

      // Poll for driver acceptance
      startPolling();
    } else {
      toast('No ambulances', data.message, 'error');
      if (btn) { btn.disabled = false; btn.innerHTML = '🚑 Request Ambulance'; }
    }
  } catch(e) {
    toast('Error', e.message, 'error');
    if (btn) { btn.disabled = false; btn.innerHTML = '🚑 Request Ambulance'; }
  }
}

function renderBookingResult(data) {
  const div = document.getElementById('book-result');
  if (!div) return;
  div.innerHTML = `
    <div class="info-box mt-16">
      <h3>🚑 Request Sent <span class="badge badge-${data.status}" style="font-size:11px">${data.status}</span></h3>
      <div class="info-grid">
        <div class="info-item"><label>Request ID</label><span>#${data.requestId}</span></div>
        <div class="info-item"><label>Priority</label><span><span class="badge badge-${data.priority}">${data.priority}</span>${data.panic?' 🚨':''}</span></div>
        <div class="info-item"><label>Drivers Notified</label><span>${data.candidateCount||'—'}</span></div>
        <div class="info-item"><label>Est. Distance</label><span class="text-blue">${data.distance!=null?data.distance+' km':'Calculating…'}</span></div>
        <div class="info-item"><label>Est. ETA</label><span class="big">${data.eta!=null?data.eta+' min':'—'}</span></div>
        <div class="info-item"><label>Status</label><span id="req-status-badge"><span class="badge badge-${data.status}">${data.status}</span></span></div>
      </div>
      ${data.hospitalName ? `
      <div class="hospital-box">
        <span class="hosp-icon">🏥</span>
        <div>
          <h4>${esc(data.hospitalName)}</h4>
          <p>${esc(data.hospitalAddress||'')} · ${data.hospitalDistance!=null?data.hospitalDistance+' km away':''} · ${esc(data.hospitalPhone||'')}</p>
          <p style="color:var(--t3);margin-top:2px">${esc(data.hospitalSpeciality||'')}</p>
        </div>
      </div>` : ''}
      <div class="btn-row">
        <button class="btn btn-ghost btn-sm" onclick="pollOnce()">🔄 Refresh Status</button>
        <button class="btn btn-danger btn-sm" onclick="cancelRequest()">✕ Cancel</button>
      </div>
      <div id="driver-accept-notice" style="margin-top:12px"></div>
    </div>`;
}

function startPolling() {
  if (S.pollTimer) clearInterval(S.pollTimer);
  S.pollTimer = setInterval(pollOnce, 5000);
}

async function pollOnce() {
  if (!S.activeReqId) return;
  try {
    const data = await api(`/api/emergency/request/${S.activeReqId}`);
    if (!data.success) return;

    const badge = document.getElementById('req-status-badge');
    if (badge) badge.innerHTML = `<span class="badge badge-${data.status}">${data.status}</span>`;

    const notice = document.getElementById('driver-accept-notice');

    if (data.status === 'ACCEPTED' || data.status === 'ARRIVING' || data.status === 'ARRIVED') {
      if (notice && data.driverName) {
        notice.innerHTML = `<div class="alert alert-success">✅ <strong>${esc(data.driverName)}</strong> accepted! Vehicle: ${esc(data.vehicleNumber||'')} · ETA: ${data.eta} min</div>`;
      }
      toast('Driver accepted!', `${data.driverName} · ${data.vehicleNumber}`, 'success');

      if (data.ambulanceLat && data.ambulanceLon) {
        if (!S.map) initMap(S.lat||11.6643, S.lon||78.1460, 14);
        placeAmb(data.ambulanceLat, data.ambulanceLon, data.vehicleNumber);
        if (S.lat) {
          placePatient(S.lat, S.lon);
          drawRoute(data.ambulanceLat, data.ambulanceLon, S.lat, S.lon);
          startSimulation(data.ambulanceLat, data.ambulanceLon, S.lat, S.lon, data.ambulanceId);
        }
      }
      if (data.status === 'ARRIVED') {
        toast('🚑 Ambulance Arrived!', 'Help is here.', 'success');
        clearInterval(S.pollTimer);
      }
    }

    if (data.status === 'COMPLETED' || data.status === 'CANCELLED') {
      clearInterval(S.pollTimer);
      if (S.simulTimer) clearInterval(S.simulTimer);
      if (notice) notice.innerHTML = `<div class="alert alert-info">Request ${data.status.toLowerCase()}.</div>`;
    }
  } catch(e) {}
}

async function cancelRequest() {
  if (!S.activeReqId) return;
  if (!confirm('Cancel this emergency request?')) return;
  try {
    await api(`/api/emergency/request/${S.activeReqId}/status`, 'PUT', {status:'CANCELLED'});
    S.activeReqId = null;
    clearInterval(S.pollTimer);
    clearInterval(S.simulTimer);
    const div = document.getElementById('book-result');
    if (div) div.innerHTML = '';
    const btn = document.getElementById('book-btn');
    if (btn) { btn.disabled = false; btn.innerHTML = '🚑 Request Ambulance'; }
    toast('Cancelled', 'Your request was cancelled.', 'info');
  } catch(e) { toast('Error','','error'); }
}

async function checkExistingRequest() {
  try {
    const data = await api(`/api/emergency/patient/${S.user.id}/active`);
    if (data.success) {
      S.activeReqId = data.requestId;
      renderBookingResult(data);
      startPolling();
      if (data.ambulanceLat && S.lat) {
        placeAmb(data.ambulanceLat, data.ambulanceLon, data.vehicleNumber);
        drawRoute(data.ambulanceLat, data.ambulanceLon, S.lat, S.lon);
      }
    }
  } catch(e) {}
}

async function initPatientHistory() {
  try {
    const rows = await api(`/api/emergency/patient/${S.user.id}/history`);
    const tb = document.getElementById('hist-tbody');
    if (!rows.length) { tb.innerHTML = emptyRow(9, 'No requests yet'); return; }
    tb.innerHTML = rows.map(r => `<tr>
      <td>${r.id}</td>
      <td><span class="badge badge-${r.priority}">${r.priority}</span>${r.panic?' 🚨':''}</td>
      <td class="mono">${esc(r.vehicleNumber||'—')}</td>
      <td>${esc(r.driverName||'—')}</td>
      <td class="text-sm">${esc(r.hospitalName||'—')}</td>
      <td>${r.distance!=null?r.distance+' km':'—'}</td>
      <td>${r.eta!=null?r.eta+' min':'—'}</td>
      <td><span class="badge badge-${r.status}">${r.status}</span></td>
      <td class="text-sm text-muted">${fmtDate(r.requestedAt)}</td>
    </tr>`).join('');
  } catch(e) { toast('Error','','error'); }
}

// ═══════════════════════════════════════════════════════════
//  DRIVER CONSOLE
// ═══════════════════════════════════════════════════════════
async function initDriverConsole() {
  await loadDriverVehicle();
  await loadDriverPending();
  await loadDriverActive();
  initDriverMap();
  // Poll every 8s
  S.pollTimer = setInterval(async () => {
    await loadDriverPending();
    await loadDriverActive();
  }, 8000);
}

async function loadDriverVehicle() {
  const el = document.getElementById('drv-vehicle-info');
  if (!el) return;
  try {
    const d = await api(`/api/ambulances/driver/${S.user.id}`);
    if (!d) { el.innerHTML = '<div class="alert alert-warning">No ambulance assigned.</div>'; return; }
    S.driverAmbId = d.id;
    el.innerHTML = `
      <div class="info-grid">
        <div class="info-item"><label>Vehicle</label><span class="mono" style="font-size:20px;color:var(--cyan)">${esc(d.vehicleNumber)}</span></div>
        <div class="info-item"><label>Status</label><span id="drv-status-badge"><span class="badge badge-${d.status}">${d.status}</span></span></div>
        <div class="info-item"><label>Latitude</label><span class="mono text-muted">${d.latitude!=null?d.latitude.toFixed(5):'—'}</span></div>
        <div class="info-item"><label>Longitude</label><span class="mono text-muted">${d.longitude!=null?d.longitude.toFixed(5):'—'}</span></div>
      </div>`;
    highlightStatus(d.status);
  } catch(e) { el.innerHTML = '<div class="alert alert-error">Failed to load vehicle.</div>'; }
}

function highlightStatus(s) {
  ['AVAILABLE','BUSY','OFFLINE'].forEach(x => {
    const el = document.getElementById('so-'+x);
    if (el) el.className = 'status-opt' + (x===s ? ' sel-'+x : '');
  });
}

async function setDriverStatus(status) {
  try {
    const ambId = S.driverAmbId;
    if (!ambId) { toast('No ambulance','','error'); return; }
    const d = await api(`/api/ambulances/${ambId}/status`, 'PUT', {status});
    if (d.success) {
      highlightStatus(status);
      const badge = document.getElementById('drv-status-badge');
      if (badge) badge.innerHTML = `<span class="badge badge-${status}">${status}</span>`;
      toast('Status updated', status, 'success');
    }
  } catch(e) { toast('Error','','error'); }
}

async function loadDriverPending() {
  const el = document.getElementById('drv-pending-info');
  if (!el) return;
  try {
    const d = await api(`/api/emergency/driver/${S.user.id}/pending`);
    if (!d.success) {
      el.innerHTML = `<div class="alert alert-info">✅ No pending requests. Stay on AVAILABLE.</div>`;
      return;
    }
    el.innerHTML = `
      <div class="driver-req-card">
        <h3>🚨 Incoming Emergency</h3>
        <div class="info-grid">
          <div class="info-item"><label>Patient</label><span>${esc(d.patientName)}</span></div>
          <div class="info-item"><label>Phone</label><span>${esc(d.patientPhone||'—')}</span></div>
          <div class="info-item"><label>Address</label><span>${esc(d.patientAddress||'—')}</span></div>
          <div class="info-item"><label>Priority</label><span><span class="badge badge-${d.priority}">${d.priority}</span>${d.panic?' 🚨':''}</span></div>
          <div class="info-item"><label>Distance</label><span class="text-blue">${d.distance!=null?d.distance+' km':'—'}</span></div>
          <div class="info-item"><label>ETA</label><span style="font-family:var(--fd);font-size:22px;color:var(--yellow)">${d.eta!=null?d.eta+' min':'—'}</span></div>
        </div>
        <div class="btn-row" style="margin-top:16px">
          <button class="btn btn-green" onclick="acceptRequest(${d.requestId})">✅ Accept</button>
          <button class="btn btn-danger" onclick="rejectRequest(${d.requestId})">✗ Reject</button>
        </div>
      </div>`;
  } catch(e) {}
}

async function acceptRequest(reqId) {
  try {
    const d = await api(`/api/emergency/driver/${S.user.id}/accept/${reqId}`, 'POST');
    if (d.success) {
      toast('Accepted!', 'Navigate to patient. ETA: ' + d.eta + ' min', 'success');
      await loadDriverPending();
      await loadDriverActive();
    } else {
      toast('Cannot accept', d.message, 'error');
    }
  } catch(e) { toast('Error','','error'); }
}

async function rejectRequest(reqId) {
  try {
    const d = await api(`/api/emergency/driver/${S.user.id}/reject/${reqId}`, 'POST');
    toast('Rejected', 'Request passed to next driver.', 'warning');
    await loadDriverPending();
  } catch(e) { toast('Error','','error'); }
}

async function loadDriverActive() {
  const el = document.getElementById('drv-active-info');
  if (!el) return;
  try {
    const d = await api(`/api/emergency/driver/${S.user.id}/active`);
    if (!d.success) {
      el.innerHTML = '<div class="alert alert-info">No active trip assigned.</div>';
      return;
    }
    el.innerHTML = `
      <div class="info-grid mb-16">
        <div class="info-item"><label>Patient</label><span>${esc(d.patientName)}</span></div>
        <div class="info-item"><label>Phone</label><span>${esc(d.patientPhone||'—')}</span></div>
        <div class="info-item"><label>Address</label><span>${esc(d.patientAddress||'—')}</span></div>
        <div class="info-item"><label>Priority</label><span><span class="badge badge-${d.priority}">${d.priority}</span></span></div>
        <div class="info-item"><label>Distance</label><span>${d.distance!=null?d.distance+' km':'—'}</span></div>
        <div class="info-item"><label>Status</label><span><span class="badge badge-${d.status}">${d.status}</span></span></div>
      </div>
      ${d.hospitalName ? `<div class="hospital-box mb-16"><span class="hosp-icon">🏥</span><div><h4>${esc(d.hospitalName)}</h4><p>${esc(d.hospitalAddress||'')} · ${esc(d.hospitalPhone||'')}</p></div></div>` : ''}
      <div class="btn-row">
        <button class="btn btn-blue btn-sm" onclick="updateTrip(${d.requestId},'ARRIVING')">🚀 Arriving</button>
        <button class="btn btn-yellow btn-sm" onclick="updateTrip(${d.requestId},'ARRIVED')">📍 Arrived</button>
        <button class="btn btn-green btn-sm" onclick="updateTrip(${d.requestId},'COMPLETED')">🏁 Complete</button>
      </div>`;

    if (d.patientLat && S.map) {
      placePatient(d.patientLat, d.patientLon, 'Patient: ' + d.patientName);
      S.map.setView([d.patientLat, d.patientLon], 14);
    }
  } catch(e) {}
}

async function updateTrip(reqId, status) {
  try {
    const d = await api(`/api/emergency/request/${reqId}/status`, 'PUT', {status});
    toast('Updated', 'Status: ' + status, 'success');
    if (status === 'COMPLETED' || status === 'CANCELLED') {
      await setDriverStatus('AVAILABLE');
    }
    await loadDriverActive();
    await loadDriverPending();
  } catch(e) { toast('Error','','error'); }
}

function initDriverMap() {
  const map = initMap(11.6643, 78.1460, 13);
  if (!S.driverAmbId) return;
  api(`/api/ambulances/${S.driverAmbId}`).then(d => {
    if (d && d.latitude) {
      placeAmb(d.latitude, d.longitude, d.vehicleNumber);
      map.setView([d.latitude, d.longitude], 14);
    }
  }).catch(()=>{});
}

// ═══════════════════════════════════════════════════════════
//  UTILS
// ═══════════════════════════════════════════════════════════
async function api(url, method = 'GET', body = null) {
  const opts = { method, headers: {'Content-Type':'application/json'} };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(url, opts);
  return await res.json();
}

function esc(s) {
  if (s == null) return '';
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function set(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val ?? '—';
}

function emptyRow(cols, msg) {
  return `<tr><td colspan="${cols}" style="text-align:center;padding:32px;color:var(--t3)">${msg}</td></tr>`;
}

function fmtDate(s) {
  if (!s) return '—';
  try { return new Date(s).toLocaleString('en-IN', {dateStyle:'short', timeStyle:'short'}); } catch(e){ return s; }
}

function fmtDateTime(s) {
  if (!s) return '—';
  try {
    const d = new Date(s);
    return d.toLocaleTimeString('en-IN', {hour:'2-digit',minute:'2-digit',second:'2-digit'});
  } catch(e){ return s; }
}
