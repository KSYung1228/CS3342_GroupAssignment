/* ============================================================
   app.js – Commercial Shop Rental System Frontend
   ============================================================ */

const API = 'http://localhost:8080/api';

// ── State ─────────────────────────────────────────────────────────────────────
let currentUser  = null;   // { id, username/email, role } or merchant obj
let userType     = null;   // 'admin' | 'merchant'
let shopTypes    = [];
let buildings    = [];
let currentView  = 'grid'; // 'grid' | 'list'
let isAdminMode  = false;

// ── Utility ───────────────────────────────────────────────────────────────────

async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(API + path, opts);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || res.statusText);
  }
  return res.json();
}

function showScreen(id) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.getElementById(id).classList.add('active');
}

function showMerchantPage(id) {
  document.querySelectorAll('#merchantDash .page').forEach(p => p.classList.remove('active'));
  document.getElementById(id).classList.add('active');
  if (id === 'shopBrowser') loadShops();
  if (id === 'myContracts') loadMyContracts();
  if (id === 'myPayments')  loadMyPayments();
}

function showAdminPage(id) {
  document.querySelectorAll('#adminDash .page').forEach(p => p.classList.remove('active'));
  document.getElementById(id).classList.add('active');
  if (id === 'adminDashboard') loadAdminDashboard();
  if (id === 'adminBuildings') loadBuildingTable();
  if (id === 'adminShops')     loadAdminShopsPage();
  if (id === 'adminMerchants') loadMerchantTable();
  if (id === 'adminContracts') loadAdminContracts();
  if (id === 'adminPayments')  loadAdminPayments();
  if (id === 'adminReports')   loadReport();
}

function closeModal(id) { document.getElementById(id).style.display = 'none'; }
function openModal(id)  { document.getElementById(id).style.display = 'flex'; }

function badge(text, cls) { return `<span class="badge ${cls}">${text}</span>`; }

function statusBadge(s) {
  const map = {
    AVAILABLE:'badge-green', ACTIVE:'badge-green', APPROVED:'badge-green', CONFIRMED:'badge-green',
    OCCUPIED:'badge-yellow', PENDING:'badge-yellow', PENDING_APPROVAL:'badge-yellow',
    MAINTENANCE:'badge-red', REJECTED:'badge-red', TERMINATED:'badge-red', NON_COMPLIANT:'badge-red',
    EXPIRED:'badge-gray', REFUNDED:'badge-gray', COMPLIANT:'badge-blue'
  };
  return badge(s, map[s] || 'badge-gray');
}

// ── Auth ──────────────────────────────────────────────────────────────────────

function switchLoginTab(tab) {
  document.querySelectorAll('.tab-btn').forEach((b, i) =>
    b.classList.toggle('active', (i === 0) === (tab === 'merchant')));
  document.getElementById('merchantLoginForm').style.display = tab === 'merchant' ? '' : 'none';
  document.getElementById('adminLoginForm').style.display    = tab === 'admin'    ? '' : 'none';
}

async function merchantLogin() {
  const email = document.getElementById('mEmail').value;
  const pass  = document.getElementById('mPass').value;
  try {
    const m = await api('POST', '/auth/merchant', { email, password: pass });
    currentUser = m; userType = 'merchant'; isAdminMode = false;
    document.getElementById('mUserName').textContent = m.name;
    await initMerchantDash();
    showScreen('merchantDash');
  } catch (e) { document.getElementById('loginErr').textContent = e.message; }
}

async function adminLogin() {
  const username = document.getElementById('aUser').value;
  const pass     = document.getElementById('aPass').value;
  try {
    const a = await api('POST', '/auth/admin', { username, password: pass });
    currentUser = a; userType = 'admin'; isAdminMode = true;
    document.getElementById('aUserName').textContent = a.username;
    await initAdminDash();
    showScreen('adminDash');
  } catch (e) { document.getElementById('loginErr').textContent = e.message; }
}

function logout() {
  currentUser = null; userType = null; isAdminMode = false;
  showScreen('loginScreen');
}

function showRegisterScreen() { showScreen('registerScreen'); }

async function registerMerchant() {
  const merchant = {
    name:            document.getElementById('regName').value,
    email:           document.getElementById('regEmail').value,
    passwordHash:    document.getElementById('regPass').value,
    phone:           document.getElementById('regPhone').value,
    businessLicense: document.getElementById('regLicense').value
  };
  try {
    await api('POST', '/merchants/register', merchant);
    document.getElementById('regMsg').className = 'success-msg';
    document.getElementById('regMsg').textContent = 'Registration submitted. Awaiting admin approval.';
  } catch (e) {
    document.getElementById('regMsg').className = 'error-msg';
    document.getElementById('regMsg').textContent = e.message;
  }
}

// ── Init ──────────────────────────────────────────────────────────────────────

async function initMerchantDash() {
  [shopTypes, buildings] = await Promise.all([
    api('GET', '/shop-types'),
    api('GET', '/buildings')
  ]);
  populateFilters();
  await loadShops();
}

async function initAdminDash() {
  [shopTypes, buildings] = await Promise.all([
    api('GET', '/shop-types'),
    api('GET', '/buildings')
  ]);
  await loadAdminDashboard();
}

// ── Merchant: Shops ───────────────────────────────────────────────────────────

function populateFilters() {
  const bSel = document.getElementById('filterBuilding');
  bSel.innerHTML = '<option value="">All Buildings</option>';
  buildings.forEach(b => {
    const o = document.createElement('option');
    o.value = b.id; o.textContent = b.name; bSel.appendChild(o);
  });
  const tSel = document.getElementById('filterType');
  tSel.innerHTML = '<option value="">All Types</option>';
  shopTypes.forEach(t => {
    const o = document.createElement('option');
    o.value = t.id; o.textContent = t.typeName; tSel.appendChild(o);
  });
}

function setView(v) {
  currentView = v;
  document.getElementById('btnGridView').classList.toggle('active', v === 'grid');
  document.getElementById('btnListView').classList.toggle('active', v === 'list');
  document.getElementById('floorPlanView').style.display = v === 'grid' ? '' : 'none';
  document.getElementById('listView').style.display      = v === 'list' ? '' : 'none';
}

async function loadShops() {
  let shops = await api('GET', '/shops');
  const bFilter = document.getElementById('filterBuilding')?.value;
  const tFilter = document.getElementById('filterType')?.value;
  const sFilter = document.getElementById('filterStatus')?.value;

  if (bFilter) shops = shops.filter(s => s.buildingId === bFilter);
  if (tFilter) shops = shops.filter(s => s.shopTypeId === tFilter);
  if (sFilter) shops = shops.filter(s => s.status === sFilter);

  if (currentView === 'grid') {
    renderMerchantFloorPlan(shops);
  } else {
    renderShopList(shops);
  }
}

function shopTypeName(id) { return shopTypes.find(t => t.id === id)?.typeName || id; }
function buildingName(id) { return buildings.find(b => b.id === id)?.name || id; }

function renderMerchantFloorPlan(shops) {
  const container = document.getElementById('floorPlanContainer');
  container.innerHTML = '';

  // Group by building and floor
  const byFloor = {};
  shops.forEach(s => {
    const key = `${s.buildingId}__F${s.floor}`;
    if (!byFloor[key]) byFloor[key] = { buildingId: s.buildingId, floor: s.floor, shops: [] };
    byFloor[key].shops.push(s);
  });

  if (Object.keys(byFloor).length === 0) {
    container.innerHTML = '<p style="color:var(--muted);padding:20px">No shops match the filter.</p>';
    return;
  }

  Object.values(byFloor).sort((a,b)=>a.floor-b.floor).forEach(fl => {
    const bld = buildings.find(b => b.id === fl.buildingId);
    const rows = bld?.gridRows || 4;
    const cols = bld?.gridCols || 5;

    const section = document.createElement('div');
    section.className = 'floor-section';
    section.innerHTML = `<h3>${bld?.name || fl.buildingId} – Floor ${fl.floor}</h3>`;

    const grid = document.createElement('div');
    grid.className = 'floor-grid';
    grid.style.gridTemplateColumns = `repeat(${cols}, 96px)`;
    grid.style.gridTemplateRows    = `repeat(${rows}, 72px)`;

    const occupied = {};
    fl.shops.forEach(s => { occupied[`${s.gridRow},${s.gridCol}`] = s; });

    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const shop = occupied[`${r},${c}`];
        const cell = document.createElement('div');
        if (shop) {
          cell.className = `grid-cell ${shop.status}`;
          if (shop.imageUrl) {
            cell.classList.add('has-image');
            cell.style.backgroundImage = `url(${shop.imageUrl})`;
            cell.innerHTML = `
              <div class="cell-overlay">
                <span class="cell-num">${shop.number}</span>
                <span class="cell-type">${shopTypeName(shop.shopTypeId)}</span>
                <span class="cell-rent">HK$${shop.baseRent?.toLocaleString()}</span>
              </div>
              <span class="cell-status-badge">${shop.status}</span>`;
          } else {
            cell.innerHTML = `
              <span class="cell-num">${shop.number}</span>
              <span class="cell-type">${shopTypeName(shop.shopTypeId)}</span>
              <span class="cell-rent">HK$${shop.baseRent?.toLocaleString()}</span>
              <span class="cell-status-badge">${shop.status}</span>`;
          }
          if (shop.status === 'AVAILABLE') {
            cell.title = `Click to view details & apply for lease`;
            cell.onclick = () => openShopDetail(shop);
          } else {
            cell.title = shop.status;
          }
        } else {
          cell.className = 'grid-cell empty';
          cell.innerHTML = '<span style="font-size:.65rem;color:#bbb">—</span>';
        }
        grid.appendChild(cell);
      }
    }
    section.appendChild(grid);
    container.appendChild(section);
  });
}

function renderShopList(shops) {
  const list = document.getElementById('shopList');
  list.innerHTML = '';
  if (!shops.length) {
    list.innerHTML = '<p style="color:var(--muted)">No shops found.</p>'; return;
  }
  shops.forEach(s => {
    const card = document.createElement('div');
    card.className = 'shop-card';
    card.onclick = () => openShopDetail(s);
    card.innerHTML = `
      <div class="card-img">
        ${s.imageUrl
          ? `<img src="${s.imageUrl}" alt="${s.number}" />`
          : '🏪'}
      </div>
      <div class="card-body">
        <h4>${s.number} – ${shopTypeName(s.shopTypeId)}</h4>
        <p>${buildingName(s.buildingId)} · Floor ${s.floor}</p>
        <p>Area: ${s.area} sq ft</p>
        <p>Rent: HK$${s.baseRent?.toLocaleString()}/mo</p>
        <p>${statusBadge(s.status)}</p>
      </div>`;
    list.appendChild(card);
  });
}

function openShopDetail(shop) {
  const type = shopTypes.find(t => t.id === shop.shopTypeId);
  const bld  = buildings.find(b => b.id === shop.buildingId);
  const img  = shop.imageUrl
    ? `<img src="${shop.imageUrl}" alt="${shop.number}" class="shop-detail-img" onerror="this.style.display='none'" />`
    : `<div style="height:120px;background:#e2e8f0;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:3rem;margin-bottom:16px">🏪</div>`;

  const rentCalc = type ? (shop.baseRent * type.rentRatio).toLocaleString('en-HK') : '—';

  document.getElementById('shopModalContent').innerHTML = `
    ${img}
    <h3>${shop.number} – ${type?.typeName || 'Unknown Type'}</h3>
    <div class="shop-detail-info">
      <p><strong>Building:</strong> ${bld?.name} · Floor ${shop.floor}</p>
      <p><strong>Area:</strong> ${shop.area} sq ft</p>
      <p><strong>Base Rent:</strong> HK$${shop.baseRent?.toLocaleString()}/month</p>
      <p><strong>Effective Rent:</strong> HK$${rentCalc}/month</p>
      <p><strong>Location:</strong> ${shop.location || '—'}</p>
      <p><strong>Status:</strong> ${statusBadge(shop.status)}</p>
      <p><strong>Compliance:</strong> ${statusBadge(shop.complianceStatus)}</p>
      ${type?.restrictions?.length ? `<p><strong>Restrictions:</strong> ${type.restrictions.join(', ')}</p>` : ''}
      ${shop.facilities?.length ? `<p><strong>Facilities:</strong> ${shop.facilities.join(', ')}</p>` : ''}
    </div>
    ${shop.status === 'AVAILABLE' && userType === 'merchant'
      ? `<div style="margin-top:16px"><button class="btn-primary" onclick="openLeaseModal('${shop.id}','${shop.number}')">Apply for Lease</button></div>`
      : ''}`;
  openModal('shopModal');
}

// ── Merchant: Contracts ───────────────────────────────────────────────────────

async function loadMyContracts() {
  const contracts = await api('GET', `/contracts/merchant/${currentUser.id}`);
  const div = document.getElementById('contractList');
  if (!contracts.length) { div.innerHTML = '<p>No contracts yet.</p>'; return; }
  div.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop</th><th>Start</th><th>End</th><th>Rent/mo</th><th>State</th><th>Action</th></tr></thead>
    <tbody>${contracts.map(c => `<tr>
      <td>${c.id.substring(0,8)}…</td>
      <td>${c.shopId}</td>
      <td>${c.startTime}</td>
      <td>${c.endTime}</td>
      <td>HK$${c.rentPerMonth?.toLocaleString()}</td>
      <td>${statusBadge(c.contractState)}</td>
      <td>${c.contractState === 'DRAFT'
        ? `<button class="btn-primary" onclick="submitContract('${c.id}')">Submit</button>`
        : ''}</td>
    </tr>`).join('')}</tbody></table>`;
}

async function submitContract(id) {
  try {
    await api('PUT', `/contracts/${id}/submit`);
    await loadMyContracts();
  } catch(e) { alert(e.message); }
}

// ── Merchant: Payments ────────────────────────────────────────────────────────

async function loadMyPayments() {
  const contracts = await api('GET', `/contracts/merchant/${currentUser.id}`);
  const all = [];
  for (const c of contracts) {
    const payments = await api('GET', `/payments/contract/${c.id}`);
    payments.forEach(p => { p._contractShopId = c.shopId; all.push(p); });
  }
  const div = document.getElementById('paymentList');
  if (!all.length) { div.innerHTML = '<p>No payment records.</p>'; return; }
  div.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop</th><th>Amount</th><th>Method</th><th>Time</th><th>Status</th></tr></thead>
    <tbody>${all.map(p => `<tr>
      <td>${p.id.substring(0,8)}…</td>
      <td>${p._contractShopId}</td>
      <td>HK$${p.amount?.toLocaleString()}</td>
      <td>${p.payMethod}</td>
      <td>${p.payTime?.replace('T',' ').substring(0,16)}</td>
      <td>${statusBadge(p.status)}</td>
    </tr>`).join('')}</tbody></table>`;
}

// ── Lease Application ─────────────────────────────────────────────────────────

function openLeaseModal(shopId, shopNumber) {
  closeModal('shopModal');
  document.getElementById('leaseShopId').value = shopId;
  document.getElementById('leaseShopInfo').textContent = `Applying for: ${shopNumber}`;
  const today = new Date().toISOString().split('T')[0];
  document.getElementById('leaseStart').value = today;
  const endDate = new Date(); endDate.setFullYear(endDate.getFullYear() + 1);
  document.getElementById('leaseEnd').value = endDate.toISOString().split('T')[0];
  openModal('leaseModal');
}

async function submitLease(e) {
  e.preventDefault();
  const body = {
    merchantId: currentUser.id,
    shopId:     document.getElementById('leaseShopId').value,
    startTime:  document.getElementById('leaseStart').value,
    endTime:    document.getElementById('leaseEnd').value
  };
  try {
    const contract = await api('POST', '/contracts', body);
    await api('PUT', `/contracts/${contract.id}/submit`);
    closeModal('leaseModal');
    alert('Lease application submitted successfully!');
    await loadMyContracts();
    showMerchantPage('myContracts');
  } catch(e) { alert(e.message); }
}

// ── Admin: Dashboard ──────────────────────────────────────────────────────────

async function loadAdminDashboard() {
  const [report, log] = await Promise.all([
    api('GET', '/reports'),
    api('GET', '/audit-log')
  ]);
  const cards = [
    { label: 'Total Shops',    value: report.totalShops },
    { label: 'Occupied',       value: report.occupiedShops },
    { label: 'Available',      value: report.availableShops },
    { label: 'Occupancy Rate', value: (report.occupancyRate * 100).toFixed(1) + '%' },
    { label: 'Total Revenue',  value: 'HK$' + report.totalRevenue?.toLocaleString() },
    { label: 'Contracts',      value: report.totalContracts },
    { label: 'Pending Merchants', value: report.pendingMerchants }
  ];
  document.getElementById('reportCards').innerHTML = cards.map(c =>
    `<div class="report-card"><div class="rc-label">${c.label}</div><div class="rc-value">${c.value}</div></div>`
  ).join('');
  document.getElementById('auditLogBox').innerHTML =
    (log.length ? log.slice(-50).reverse() : ['No events yet.']).map(l => `<div>${l}</div>`).join('');
}

// ── Admin: Buildings ──────────────────────────────────────────────────────────

async function loadBuildingTable() {
  const list = await api('GET', '/buildings');
  document.getElementById('buildingTable').innerHTML = `<table>
    <thead><tr><th>Name</th><th>Address</th><th>Floors</th><th>Status</th><th>Actions</th></tr></thead>
    <tbody>${list.map(b => `<tr>
      <td>${b.name}</td><td>${b.address}</td><td>${b.totalFloors}</td>
      <td>${statusBadge(b.status)}</td>
      <td><button class="btn-secondary" onclick='openBuildingModal(${JSON.stringify(b)})'>Edit</button></td>
    </tr>`).join('')}</tbody></table>`;
}

function openBuildingModal(b) {
  document.getElementById('bldId').value      = b?.id || '';
  document.getElementById('bldName').value    = b?.name || '';
  document.getElementById('bldAddress').value = b?.address || '';
  document.getElementById('bldFloors').value  = b?.totalFloors || 5;
  document.getElementById('bldRows').value    = b?.gridRows || 4;
  document.getElementById('bldCols').value    = b?.gridCols || 5;
  document.getElementById('bldStatus').value  = b?.status || 'ACTIVE';
  openModal('buildingModal');
}

async function submitBuilding(e) {
  e.preventDefault();
  const b = {
    id:          document.getElementById('bldId').value || undefined,
    name:        document.getElementById('bldName').value,
    address:     document.getElementById('bldAddress').value,
    totalFloors: parseInt(document.getElementById('bldFloors').value),
    gridRows:    parseInt(document.getElementById('bldRows').value),
    gridCols:    parseInt(document.getElementById('bldCols').value),
    status:      document.getElementById('bldStatus').value,
    allowedShopTypeIds: shopTypes.map(t => t.id)
  };
  await api('POST', '/buildings', b);
  closeModal('buildingModal');
  buildings = await api('GET', '/buildings');
  await loadBuildingTable();
}

// ── Admin: Shops Floor Plan ───────────────────────────────────────────────────

async function loadAdminShopsPage() {
  const sel = document.getElementById('adminBuildingSelect');
  sel.innerHTML = '<option value="">Select Building</option>';
  buildings.forEach(b => {
    const o = document.createElement('option');
    o.value = b.id; o.textContent = b.name; sel.appendChild(o);
  });
  await loadAdminShopTable();
}

async function loadAdminFloorPlan() {
  const buildingId = document.getElementById('adminBuildingSelect').value;
  const container  = document.getElementById('adminFloorPlanContainer');
  if (!buildingId) { container.innerHTML = ''; return; }

  const shops = await api('GET', `/buildings/${buildingId}/floor-plan`);
  const bld   = buildings.find(b => b.id === buildingId);
  const rows  = bld?.gridRows || 4;
  const cols  = bld?.gridCols || 5;

  const byFloor = {};
  shops.forEach(s => {
    if (!byFloor[s.floor]) byFloor[s.floor] = {};
    byFloor[s.floor][`${s.gridRow},${s.gridCol}`] = s;
  });

  container.innerHTML = '';
  const floors = [...new Set(shops.map(s => s.floor))].sort((a,b) => a-b);
  if (!floors.length) floors.push(1);

  floors.forEach(floor => {
    const section = document.createElement('div');
    section.className = 'floor-section';
    section.innerHTML = `<h3>Floor ${floor}</h3>`;

    const grid = document.createElement('div');
    grid.className = 'floor-grid';
    grid.style.gridTemplateColumns = `repeat(${cols}, 96px)`;
    grid.style.gridTemplateRows    = `repeat(${rows}, 72px)`;

    const occupied = byFloor[floor] || {};

    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const shop = occupied[`${r},${c}`];
        const cell = document.createElement('div');
        if (shop) {
          cell.className = `grid-cell ${shop.status} admin-mode`;
          if (shop.imageUrl) {
            cell.classList.add('has-image');
            cell.style.backgroundImage = `url(${shop.imageUrl})`;
            cell.innerHTML = `<div class="cell-overlay">
              <span class="cell-num">${shop.number}</span>
              <span class="cell-type">${shopTypeName(shop.shopTypeId)}</span>
            </div>`;
          } else {
            cell.innerHTML = `
              <span class="cell-num">${shop.number}</span>
              <span class="cell-type">${shopTypeName(shop.shopTypeId)}</span>
              <span class="cell-status-badge">${shop.status}</span>`;
          }
          cell.title = 'Click to edit';
          cell.onclick = () => openShopModal(shop, buildingId, floor, r, c);
        } else {
          cell.className = 'grid-cell empty admin-mode';
          cell.innerHTML = '<span style="font-size:.65rem;color:#999">+ Add</span>';
          cell.style.cursor = 'pointer';
          cell.onclick = () => openShopModal(null, buildingId, floor, r, c);
        }
        grid.appendChild(cell);
      }
    }
    section.appendChild(grid);
    container.appendChild(section);
  });
}

function openShopModal(shop, buildingId, floor, gridRow, gridCol) {
  document.getElementById('shopEditTitle').textContent = shop ? `Edit Shop ${shop.number}` : 'Add New Shop';
  document.getElementById('seId').value         = shop?.id || '';
  document.getElementById('seBuildingId').value = shop?.buildingId || buildingId || '';
  document.getElementById('seNumber').value     = shop?.number || '';
  document.getElementById('seFloor').value      = shop?.floor || floor || 1;
  document.getElementById('seArea').value       = shop?.area || '';
  document.getElementById('seBaseRent').value   = shop?.baseRent || '';
  document.getElementById('seLocation').value   = shop?.location || '';
  document.getElementById('seGridRow').value    = shop?.gridRow ?? gridRow ?? 0;
  document.getElementById('seGridCol').value    = shop?.gridCol ?? gridCol ?? 0;
  document.getElementById('seStatus').value     = shop?.status || 'AVAILABLE';
  document.getElementById('seCompliance').value = shop?.complianceStatus || 'COMPLIANT';
  document.getElementById('seImageUrl').value   = shop?.imageUrl || '';

  // Populate shop type select
  const typeSel = document.getElementById('seShopType');
  typeSel.innerHTML = shopTypes.map(t =>
    `<option value="${t.id}" ${shop?.shopTypeId === t.id ? 'selected' : ''}>${t.typeName}</option>`
  ).join('');

  openModal('shopEditModal');
}

async function submitShopEdit(e) {
  e.preventDefault();
  const shop = {
    id:              document.getElementById('seId').value || undefined,
    buildingId:      document.getElementById('seBuildingId').value,
    number:          document.getElementById('seNumber').value,
    floor:           parseInt(document.getElementById('seFloor').value),
    area:            parseFloat(document.getElementById('seArea').value),
    baseRent:        parseFloat(document.getElementById('seBaseRent').value),
    location:        document.getElementById('seLocation').value,
    shopTypeId:      document.getElementById('seShopType').value,
    gridRow:         parseInt(document.getElementById('seGridRow').value),
    gridCol:         parseInt(document.getElementById('seGridCol').value),
    status:          document.getElementById('seStatus').value,
    complianceStatus:document.getElementById('seCompliance').value,
    imageUrl:        document.getElementById('seImageUrl').value || null,
    facilities:      []
  };
  try {
    if (shop.id) {
      await api('PUT', `/shops/${shop.id}`, shop);
    } else {
      await api('POST', '/shops', shop);
    }
    closeModal('shopEditModal');
    await loadAdminFloorPlan();
    await loadAdminShopTable();
  } catch(e) { alert(e.message); }
}

async function loadAdminShopTable() {
  const shops = await api('GET', '/shops');
  document.getElementById('adminShopTable').innerHTML = `<table>
    <thead><tr><th>No.</th><th>Building</th><th>Floor</th><th>Type</th><th>Area</th><th>Base Rent</th><th>Status</th><th>Edit</th></tr></thead>
    <tbody>${shops.map(s => `<tr>
      <td>${s.number}</td>
      <td>${buildingName(s.buildingId)}</td>
      <td>${s.floor}</td>
      <td>${shopTypeName(s.shopTypeId)}</td>
      <td>${s.area} ft²</td>
      <td>HK$${s.baseRent?.toLocaleString()}</td>
      <td>${statusBadge(s.status)}</td>
      <td><button class="btn-secondary" onclick='openShopModal(${JSON.stringify(s)})'>Edit</button></td>
    </tr>`).join('')}</tbody></table>`;
}

// ── Admin: Merchants ──────────────────────────────────────────────────────────

async function loadMerchantTable() {
  const list = await api('GET', '/merchants');
  document.getElementById('merchantTable').innerHTML = `<table>
    <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>License</th><th>Status</th><th>Actions</th></tr></thead>
    <tbody>${list.map(m => `<tr>
      <td>${m.name}</td><td>${m.email}</td><td>${m.phone}</td>
      <td>${m.businessLicense}</td>
      <td>${statusBadge(m.status)}</td>
      <td>
        ${m.status === 'PENDING' ? `
          <button class="btn-success" onclick="approveMerchant('${m.id}',true)">Approve</button>
          <button class="btn-danger"  onclick="approveMerchant('${m.id}',false)">Reject</button>` : '—'}
      </td>
    </tr>`).join('')}</tbody></table>`;
}

async function approveMerchant(id, approved) {
  await api('PUT', `/merchants/${id}/approve?approved=${approved}`);
  await loadMerchantTable();
}

// ── Admin: Contracts ──────────────────────────────────────────────────────────

async function loadAdminContracts() {
  const contracts = await api('GET', '/contracts');
  document.getElementById('adminContractTable').innerHTML = `<table>
    <thead><tr><th>ID</th><th>Merchant</th><th>Shop</th><th>Start</th><th>End</th>
      <th>Rent/mo</th><th>State</th><th>Actions</th></tr></thead>
    <tbody>${contracts.map(c => `<tr>
      <td>${c.id.substring(0,8)}…</td>
      <td>${c.merchantId}</td>
      <td>${c.shopId}</td>
      <td>${c.startTime}</td>
      <td>${c.endTime}</td>
      <td>HK$${c.rentPerMonth?.toLocaleString()}</td>
      <td>${statusBadge(c.contractState)}</td>
      <td>
        ${c.contractState === 'PENDING_APPROVAL'
          ? `<button class="btn-success" onclick="approveContract('${c.id}')">Approve</button>
             <button class="btn-danger" onclick="terminateContract('${c.id}')">Reject</button>`
          : c.contractState === 'ACTIVE'
            ? `<button class="btn-danger" onclick="terminateContract('${c.id}')">Terminate</button>
               <button class="btn-secondary" onclick="openAdjustRent('${c.id}')">Adjust Rent</button>`
            : '—'}
      </td>
    </tr>`).join('')}</tbody></table>`;
}

async function approveContract(id) {
  await api('PUT', `/contracts/${id}/approve?adminId=${currentUser.id}`);
  await loadAdminContracts();
}

async function terminateContract(id) {
  if (!confirm('Terminate this contract?')) return;
  await api('PUT', `/contracts/${id}/terminate`);
  await loadAdminContracts();
}

async function openAdjustRent(contractId) {
  const strategy = prompt(
    'Enter rent strategy:\nSTANDARD\nPROMOTIONAL:0.20  (20% discount)\nPREMIUM:0.15  (15% surcharge)',
    'STANDARD');
  if (!strategy) return;
  try {
    await api('PUT', `/contracts/${contractId}/adjust-rent?strategy=${encodeURIComponent(strategy)}`);
    await loadAdminContracts();
  } catch(e) { alert(e.message); }
}

// ── Admin: Payments ───────────────────────────────────────────────────────────

async function loadAdminPayments() {
  const payments = await api('GET', '/payments');
  document.getElementById('adminPaymentTable').innerHTML = `<table>
    <thead><tr><th>ID</th><th>Contract</th><th>Amount</th><th>Method</th><th>Time</th><th>Status</th><th>Actions</th></tr></thead>
    <tbody>${payments.map(p => `<tr>
      <td>${p.id.substring(0,8)}…</td>
      <td>${p.contractId.substring(0,8)}…</td>
      <td>HK$${p.amount?.toLocaleString()}</td>
      <td>${p.payMethod}</td>
      <td>${p.payTime?.replace('T',' ').substring(0,16)}</td>
      <td>${statusBadge(p.status)}</td>
      <td>
        ${p.status === 'PENDING'
          ? `<button class="btn-success" onclick="confirmPayment('${p.id}')">Confirm</button>
             <button class="btn-danger"  onclick="refundPayment('${p.id}')">Refund</button>`
          : '—'}
      </td>
    </tr>`).join('')}</tbody></table>`;
}

async function confirmPayment(id) {
  await api('PUT', `/payments/${id}/confirm`);
  await loadAdminPayments();
}

async function refundPayment(id) {
  await api('PUT', `/payments/${id}/refund`);
  await loadAdminPayments();
}

// ── Admin: Reports ────────────────────────────────────────────────────────────

async function loadReport() {
  const report = await api('GET', '/reports');
  const cards = [
    { label: 'Total Shops',      value: report.totalShops },
    { label: 'Occupied',         value: report.occupiedShops },
    { label: 'Available',        value: report.availableShops },
    { label: 'Occupancy Rate',   value: (report.occupancyRate * 100).toFixed(1) + '%' },
    { label: 'Total Revenue',    value: 'HK$' + report.totalRevenue?.toLocaleString() },
    { label: 'Total Contracts',  value: report.totalContracts },
    { label: 'Pending Merchants',value: report.pendingMerchants }
  ];
  document.getElementById('reportDetail').innerHTML = cards.map(c =>
    `<div class="report-card"><div class="rc-label">${c.label}</div><div class="rc-value">${c.value}</div></div>`
  ).join('');
}

// ── Boot ──────────────────────────────────────────────────────────────────────
showScreen('loginScreen');
