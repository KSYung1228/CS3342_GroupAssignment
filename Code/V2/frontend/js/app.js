const API_BASE = "http://localhost:8080/api";

let state = emptyState();
let currentUser = null;
let selectedShopId = null;
let currentFloor = null; // currently selected floor tab

function emptyState() {
  return { users: [], permissions: {}, shops: [], contracts: [], payments: [],
           activeContractIdByUser: {}, logs: [], floors: [] };
}

// ── API ──────────────────────────────────────────────────────────────────────

async function api(method, path, body) {
  const opts = { method, headers: { "Content-Type": "application/json" } };
  if (body !== undefined) opts.body = JSON.stringify(body);
  let res;
  try { res = await fetch(`${API_BASE}${path}`, opts); }
  catch { throw new Error("Cannot connect to server. Please start the Java API first."); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

async function refreshState() {
  const data = await api("GET", "/state");
  state = { ...emptyState(), ...data };
  renderAll();
}

// ── Messages ─────────────────────────────────────────────────────────────────

function setMsg(text, isError = false) {
  const el = document.getElementById("appMessage");
  el.textContent = text;
  el.style.color = isError ? "var(--danger)" : "var(--ok)";
}

function setAuthMsg(text, isError = false) {
  const el = document.getElementById("authMessage");
  el.textContent = text;
  el.style.color = isError ? "var(--danger)" : "var(--ok)";
}

// ── Views ────────────────────────────────────────────────────────────────────

function showView(id) {
  document.querySelectorAll(".view").forEach(v => v.classList.remove("active"));
  document.getElementById(id).classList.add("active");
}

// ── Role config ───────────────────────────────────────────────────────────────

const rolePanels = {
  tenant:          ["floorPlanPanel", "tenantContracts", "tenantSales", "tenantPayments", "auditLogPanel"],
  contractmanager: ["floorPlanPanel", "managerContracts", "managerShops", "managerFloors", "auditLogPanel"],
  accounting:      ["accountingPayments", "accountingInspect", "auditLogPanel"],
  systemadmin:     ["adminUsers", "adminShops", "auditLogPanel"],
};

const panelLabels = {
  floorPlanPanel:     "Floor Plan",
  tenantContracts:    "My Contracts",
  tenantSales:        "Upload Sales",
  tenantPayments:     "My Payments",
  managerContracts:   "Contracts",
  managerShops:       "Shops",
  managerFloors:      "Floors",
  accountingPayments: "Payments",
  accountingInspect:  "Inspection",
  adminUsers:         "Users",
  adminShops:         "Shops",
  auditLogPanel:      "Activity Log",
};

// ── Auth ──────────────────────────────────────────────────────────────────────

document.getElementById("tabLogin").addEventListener("click", () => {
  document.getElementById("tabLogin").classList.add("active");
  document.getElementById("tabRegister").classList.remove("active");
  document.getElementById("loginForm").classList.add("active");
  document.getElementById("registerForm").classList.remove("active");
});

document.getElementById("tabRegister").addEventListener("click", () => {
  document.getElementById("tabRegister").classList.add("active");
  document.getElementById("tabLogin").classList.remove("active");
  document.getElementById("registerForm").classList.add("active");
  document.getElementById("loginForm").classList.remove("active");
});

document.getElementById("loginForm").addEventListener("submit", async e => {
  e.preventDefault();
  const username = document.getElementById("loginUsername").value.trim();
  const password = document.getElementById("loginPassword").value.trim();
  try {
    const data = await api("POST", "/auth/login", { username, password });
    currentUser = data.user;
    await refreshState();
    initAppView();
    showView("appView");
    setAuthMsg("");
  } catch (err) { setAuthMsg(err.message, true); }
});

document.getElementById("registerForm").addEventListener("submit", async e => {
  e.preventDefault();
  const username = document.getElementById("regUsername").value.trim();
  const password = document.getElementById("regPassword").value.trim();
  try {
    await api("POST", "/auth/register", { username, password });
    setAuthMsg("Registered successfully. Please login.");
    document.getElementById("tabLogin").click();
  } catch (err) { setAuthMsg(err.message, true); }
});

document.getElementById("btnLogout").addEventListener("click", () => {
  currentUser = null; state = emptyState(); selectedShopId = null; currentFloor = null;
  document.body.dataset.role = "";
  showView("loginView");
  document.getElementById("loginForm").reset();
  document.getElementById("registerForm").reset();
});

document.getElementById("btnResetData").addEventListener("click", async () => {
  if (!confirm("Reset all demo data?")) return;
  try {
    await api("POST", "/reset");
    await refreshState();
    setMsg("Demo data reset.");
  } catch (err) { setMsg(err.message, true); }
});

// ── App init ──────────────────────────────────────────────────────────────────

function initAppView() {
  const role = currentUser.role;
  document.getElementById("welcomeText").textContent = "Welcome, " + currentUser.username;
  document.getElementById("roleText").textContent = roleLabel(role);

  // Set role on body for CSS-based role gating
  document.body.dataset.role = role;

  // Hide all CM-only floor plan controls immediately based on role
  const isCM = role === "contractmanager";
  document.getElementById("btnDrawMode").classList.toggle("hidden", !isCM);
  document.getElementById("dragHint").classList.toggle("hidden", !isCM);
  document.getElementById("drawToolbar").classList.add("hidden");

  const nav = document.getElementById("roleNav");
  nav.innerHTML = "";
  const panels = rolePanels[role] || [];
  panels.forEach((panelId, i) => {
    const btn = document.createElement("button");
    btn.className = "nav-btn" + (i === 0 ? " active" : "");
    btn.textContent = panelLabels[panelId] || panelId;
    btn.addEventListener("click", () => {
      nav.querySelectorAll(".nav-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      document.querySelectorAll(".panel").forEach(p => p.classList.add("hidden"));
      document.getElementById(panelId).classList.remove("hidden");
      renderAll();
    });
    nav.appendChild(btn);
  });

  document.querySelectorAll(".panel").forEach(p => p.classList.add("hidden"));
  if (panels[0]) document.getElementById(panels[0]).classList.remove("hidden");
  renderAll();
}

function roleLabel(role) {
  return { tenant: "Tenant", contractmanager: "Contract Manager", accounting: "Accounting", systemadmin: "System Admin" }[role] || role;
}

// ── Render all ────────────────────────────────────────────────────────────────

function renderAll() {
  if (!currentUser) return;
  const role = currentUser.role;
  if (role === "tenant" || role === "contractmanager") renderFloorPlan();
  if (role === "tenant") { renderTenantContracts(); renderTenantPayments(); renderActiveTenantContractInfo(); }
  if (role === "contractmanager") { renderManagerContracts(); renderManagerShops(); renderManagerFloors(); }
  if (role === "accounting") { renderAccountingPayments(); renderAccountingInspect(); }
  if (role === "systemadmin") { renderAdminUsers(); renderAdminShops(); }
  renderAuditLog();
}

function badge(val) {
  const labels = {
    OPEN: "Open", CLOSED: "Closed", UNDER_REPAIR: "Under Repair",
    ACTIVE: "Active", PENDING_APPROVAL: "Pending", DRAFT: "Draft",
    TERMINATED: "Terminated", EXPIRED: "Expired",
    CONFIRMED: "Confirmed", UNVERIFIED: "Unverified",
    FNB: "F&B", POPUP: "Pop-up", SOLID: "Solid",
    tenant: "Tenant", contractmanager: "Contract Mgr",
    accounting: "Accounting", systemadmin: "Admin",
  };
  return `<span class="badge ${val}">${labels[val] || val}</span>`;
}

function fmt(n) {
  return Number(n || 0).toLocaleString("en-HK", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ── Floor Plan ────────────────────────────────────────────────────────────────

const GRID_COLS = 16;
const GRID_ROWS = 12;
const CELL_SIZE = 96; // fixed cell size in px

const drag   = { active: false, shopId: null, origX: 0, origY: 0, targetCol: 0, targetRow: 0, gridRect: null };
const resize = { active: false, shopId: null, origW: 0, origH: 0, startX: 0, startY: 0, newW: 0, newH: 0 };
const draw   = { active: false, painting: false, cells: new Set() };

function renderFloorPlan() {
  const grid = document.getElementById("floorPlanGrid");
  grid.innerHTML = "";

  const isCM = currentUser.role === "contractmanager";
  const isTenant = currentUser.role === "tenant";

  // ── Floor tabs ──────────────────────────────────────────────────────────────
  const floors = state.floors && state.floors.length > 0 ? state.floors : ["G"];
  if (!currentFloor || !floors.includes(currentFloor)) currentFloor = floors[0];

  const tabsEl = document.getElementById("floorTabs");
  tabsEl.innerHTML = "";
  floors.forEach(f => {
    const btn = document.createElement("button");
    btn.className = "floor-tab" + (f === currentFloor ? " active" : "");
    btn.textContent = f;
    btn.addEventListener("click", () => {
      currentFloor = f;
      selectedShopId = null;
      document.getElementById("shopInfoCard").classList.add("hidden");
      renderFloorPlan();
    });
    tabsEl.appendChild(btn);
  });

  // ── Filter shops to current floor ──────────────────────────────────────────
  const floorShops = state.shops.filter(s => (s.floor || "G") === currentFloor);

  // ── CM-only controls (initAppView already set these) ──

  grid.classList.toggle("draw-mode", draw.active);

  // ── Occupied set (current floor only) ─────────────────────────────────────
  const occupied = new Set();
  floorShops.forEach(s => {
    if (s.cells && s.cells.length > 0) {
      s.cells.forEach(key => occupied.add(key));
    } else {
      for (let c = s.posX; c < s.posX + s.width; c++)
        for (let r = s.posY; r < s.posY + s.height; r++)
          occupied.add(`${c},${r}`);
    }
  });

  const rentedShopIds = new Set(
    state.contracts.filter(c => c.status === "ACTIVE").map(c => c.shopId)
  );

  // Render existing shop cells
  floorShops.forEach(shop => {
    const effectiveStatus = rentedShopIds.has(shop.shopId) ? "RENTED" : shop.status;
    const hasCells = shop.cells && shop.cells.length > 0;

    if (hasCells) {
      // Render one div per cell for arbitrary shapes
      shop.cells.forEach((key, idx) => {
        const [c, r] = key.split(",").map(Number);
        const cell = document.createElement("div");
        // No border-radius on custom-shape cells — overlay handles the look
        cell.className = `floor-cell status-${effectiveStatus} no-radius${(isCM && !draw.active) ? " draggable" : ""}`;
        if (shop.shopId === selectedShopId) cell.classList.add("selected");
        cell.style.gridColumn = `${c + 1}`;
        cell.style.gridRow    = `${r + 1}`;
        cell.dataset.shopId   = shop.shopId;

        const icon = { OPEN: "✅", CLOSED: "🚫", UNDER_REPAIR: "🔧", RENTED: "🏪" }[effectiveStatus] || "❓";
        if (idx === 0) {
          // No resize handle for custom-shape shops — use redraw instead
          cell.innerHTML = `
            <span class="cell-icon">${icon}</span>
            <span class="cell-num">${shop.shopNum}</span>
            <span class="cell-type">${shop.type}</span>
          `;
        } else {
          cell.style.opacity = "0.85";
        }

        if (!draw.active) {
          cell.addEventListener("click", e => {
            if (drag.active || resize.active) return;
            const rect = e.currentTarget.getBoundingClientRect(); // capture before re-render
            selectedShopId = shop.shopId;
            renderFloorPlan();
            showShopInfoCard(shop, effectiveStatus, rect);
          });
          if (isCM) {
            cell.addEventListener("mousedown", e => {
              if (e.target.classList.contains("resize-handle")) return;
              e.preventDefault();
              startDrag(e, shop);
            });
            if (idx === 0) {
              const handle = cell.querySelector(".resize-handle");
              if (handle) handle.addEventListener("mousedown", e => {
                e.preventDefault(); e.stopPropagation(); startResize(e, shop);
              });
            }
          }
        }
        grid.appendChild(cell);
      });
    } else {
      // Rectangular shop — single spanning cell (original behaviour)
      const cell = document.createElement("div");
      cell.className = `floor-cell status-${effectiveStatus}${(isCM && !draw.active) ? " draggable" : ""}`;
      if (shop.shopId === selectedShopId) cell.classList.add("selected");
      cell.style.gridColumn = `${shop.posX + 1} / span ${shop.width}`;
      cell.style.gridRow    = `${shop.posY + 1} / span ${shop.height}`;
      cell.dataset.shopId   = shop.shopId;

      const icon = { OPEN: "✅", CLOSED: "🚫", UNDER_REPAIR: "🔧", RENTED: "🏪" }[effectiveStatus] || "❓";
      cell.innerHTML = `
        <span class="cell-icon">${icon}</span>
        <span class="cell-num">${shop.shopNum}</span>
        <span class="cell-type">${shop.type}</span>
        ${(isCM && !draw.active) ? '<span class="resize-handle" title="Drag to resize">⤡</span>' : ""}
      `;

      if (!draw.active) {
        cell.addEventListener("click", e => {
          if (drag.active || resize.active) return;
          const rect = e.currentTarget.getBoundingClientRect(); // capture before re-render
          selectedShopId = shop.shopId;
          renderFloorPlan();
          showShopInfoCard(shop, effectiveStatus, rect);
        });
        if (isCM) {
          cell.addEventListener("mousedown", e => {
            if (e.target.classList.contains("resize-handle")) return;
            e.preventDefault();
            startDrag(e, shop);
          });
          const handle = cell.querySelector(".resize-handle");
          if (handle) handle.addEventListener("mousedown", e => {
            e.preventDefault(); e.stopPropagation(); startResize(e, shop);
          });
        }
      }
      grid.appendChild(cell);
    }
  });

  // Build hole cells set (cells inside a custom-shape shop's bounding box but not in its cell list)
  const holeCells = new Set();
  floorShops.forEach(s => {
    if (!s.cells || s.cells.length === 0) return;
    const cellSet = new Set(s.cells);
    for (let c = s.posX; c < s.posX + s.width; c++)
      for (let r = s.posY; r < s.posY + s.height; r++)
        if (!cellSet.has(`${c},${r}`)) holeCells.add(`${c},${r}`);
  });

  // Render empty/hole cells — CM gets full interactive empty cells; all roles get hole cells
  if (isCM) {
    for (let r = 0; r < GRID_ROWS; r++) {
      for (let c = 0; c < GRID_COLS; c++) {
        if (occupied.has(`${c},${r}`)) continue;
        const empty = document.createElement("div");
        const isHole = holeCells.has(`${c},${r}`);
        empty.className = isHole ? "grid-hole-cell" : "grid-empty-cell";
        if (!isHole && draw.active && draw.cells.has(`${c},${r}`)) empty.classList.add("paint-selected");
        empty.style.gridColumn = `${c + 1}`;
        empty.style.gridRow    = `${r + 1}`;
        empty.dataset.col = c;
        empty.dataset.row = r;
        if (!isHole && draw.active) {
          empty.addEventListener("mousedown", e => { e.preventDefault(); startPaint(c, r); });
          empty.addEventListener("mouseenter", () => { if (draw.painting) continuePaint(c, r); });
        }
        grid.appendChild(empty);
      }
    }
  } else {
    // Non-CM: only render hole cells so they blend into the grid background
    holeCells.forEach(key => {
      const [c, r] = key.split(",").map(Number);
      const hole = document.createElement("div");
      hole.className = "grid-hole-cell";
      hole.style.gridColumn = `${c + 1}`;
      hole.style.gridRow    = `${r + 1}`;
      grid.appendChild(hole);
    });
  }

  document.getElementById("floorPlanChip").textContent =
    draw.active ? "Click & drag empty cells to paint shop shape"
    : isTenant ? "Click an available shop to request a lease"
    : isCM ? "Click · Drag to move · ⤡ Resize · ✏️ Draw new"
    : "Click a shop for details";

  // Re-draw shape overlay after grid rebuild (innerHTML wipes it)
  if (draw.active && draw.cells.size > 0) redrawShapeOverlay();

  // Draw connected shape outlines for existing cell-list shops
  if (isCM || isTenant) {
    floorShops.forEach(shop => {
      if (shop.cells && shop.cells.length > 0) {
        drawShopShapeOverlay(shop, rentedShopIds.has(shop.shopId));
      }
    });
  }

  // Auto-fit zoom removed — fixed cell size
}

// ── Drag to move ──────────────────────────────────────────────────────────────

function startDrag(e, shop) {
  drag.active  = true;
  drag.shopId  = shop.shopId;
  drag.origX   = shop.posX;
  drag.origY   = shop.posY;
  drag.gridRect = document.getElementById("floorPlanGrid").getBoundingClientRect();

  const GAP = 5;
  const CS  = CELL_SIZE + GAP;
  const PADDING = 10;

  // Which cell did the user click on? Use that as the anchor.
  const clickCol = Math.max(0, Math.min(GRID_COLS - 1,
    Math.floor((e.clientX - drag.gridRect.left - PADDING) / CS)));
  const clickRow = Math.max(0, Math.min(GRID_ROWS - 1,
    Math.floor((e.clientY - drag.gridRect.top  - PADDING) / CS)));

  // Offset from shop's top-left to the clicked cell
  drag.anchorDCol = clickCol - shop.posX;
  drag.anchorDRow = clickRow - shop.posY;
  drag.targetCol  = shop.posX;
  drag.targetRow  = shop.posY;

  const ghost = document.createElement("div");
  ghost.id = "dragGhost";
  ghost.className = "drag-ghost";
  ghost.style.width  = (shop.width  * CS - GAP) + "px";
  ghost.style.height = (shop.height * CS - GAP) + "px";
  ghost.style.left   = (shop.posX * CS + PADDING) + "px";
  ghost.style.top    = (shop.posY * CS + PADDING) + "px";
  document.getElementById("floorPlanGrid").appendChild(ghost);

  const onMove = e2 => {
    // The cell under the cursor should be the anchor cell, not the top-left
    const cursorCol = Math.floor((e2.clientX - drag.gridRect.left - PADDING) / CS);
    const cursorRow = Math.floor((e2.clientY - drag.gridRect.top  - PADDING) / CS);
    // Top-left of shop = cursor cell minus the anchor offset
    const newLeft = Math.max(0, Math.min(GRID_COLS - shop.width,  cursorCol - drag.anchorDCol));
    const newTop  = Math.max(0, Math.min(GRID_ROWS - shop.height, cursorRow - drag.anchorDRow));
    ghost.style.left = (newLeft * CS + PADDING) + "px";
    ghost.style.top  = (newTop  * CS + PADDING) + "px";
    drag.targetCol = newLeft;
    drag.targetRow = newTop;
  };

  const onUp = async () => {
    document.removeEventListener("mousemove", onMove);
    document.removeEventListener("mouseup", onUp);
    ghost.remove();
    drag.active = false;
    const col = drag.targetCol, row = drag.targetRow;
    if (col === drag.origX && row === drag.origY) return;
    const s = state.shops.find(s => s.shopId === drag.shopId);
    if (!s) return;
    const dCol = col - drag.origX;
    const dRow = row - drag.origY;
    try {
      const newCells = (s.cells && s.cells.length > 0)
        ? s.cells.map(key => {
            const [c, r] = key.split(",").map(Number);
            return `${c + dCol},${r + dRow}`;
          })
        : [];
      await api("PUT", `/shops/${s.shopId}`, {
        shopNum: s.shopNum, area: s.area, type: s.type, status: s.status,
        posX: col, posY: row, width: s.width, height: s.height,
        cells: newCells,
      });
      await refreshState();
      setMsg(`Shop ${s.shopNum} moved.`);
    } catch (err) { setMsg(err.message, true); await refreshState(); }
  };

  document.addEventListener("mousemove", onMove);
  document.addEventListener("mouseup", onUp);
}

// ── Resize ────────────────────────────────────────────────────────────────────

function startResize(e, shop) {
  resize.active = true;
  resize.shopId = shop.shopId;
  resize.origW  = shop.width;
  resize.origH  = shop.height;
  resize.startX = e.clientX;
  resize.startY = e.clientY;
  resize.newW   = shop.width;
  resize.newH   = shop.height;

  const onMove = e2 => {
    const CS = CELL_SIZE + 5;
    const dCol = Math.round((e2.clientX - resize.startX) / CS);
    const dRow = Math.round((e2.clientY - resize.startY) / CS);
    resize.newW = Math.max(1, Math.min(GRID_COLS - shop.posX, resize.origW + dCol));
    resize.newH = Math.max(1, Math.min(GRID_ROWS - shop.posY, resize.origH + dRow));
    const cell = document.querySelector(`.floor-cell[data-shop-id="${resize.shopId}"]`);
    if (cell) {
      cell.style.gridColumn = `${shop.posX + 1} / span ${resize.newW}`;
      cell.style.gridRow    = `${shop.posY + 1} / span ${resize.newH}`;
    }
  };

  const onUp = async () => {
    document.removeEventListener("mousemove", onMove);
    document.removeEventListener("mouseup", onUp);
    resize.active = false;
    if (resize.newW === resize.origW && resize.newH === resize.origH) return;
    const s = state.shops.find(s => s.shopId === resize.shopId);
    if (!s) return;
    try {
      await api("PUT", `/shops/${s.shopId}`, {
        shopNum: s.shopNum, area: s.area, type: s.type, status: s.status,
        posX: s.posX, posY: s.posY, width: resize.newW, height: resize.newH,
        cells: [],   // resizing converts to rectangular
      });
      await refreshState();
      setMsg(`Shop ${s.shopNum} resized to ${resize.newW}×${resize.newH}.`);
    } catch (err) { setMsg(err.message, true); await refreshState(); }
  };

  document.addEventListener("mousemove", onMove);
  document.addEventListener("mouseup", onUp);
}

// ── Draw new shop ─────────────────────────────────────────────────────────────

function startPaint(col, row) {
  draw.painting = true;
  draw.cells.add(`${col},${row}`);
  refreshDrawHighlight();

  const onUp = () => {
    document.removeEventListener("mouseup", onUp);
    draw.painting = false;
  };
  document.addEventListener("mouseup", onUp);
}

function continuePaint(col, row) {
  draw.cells.add(`${col},${row}`);
  refreshDrawHighlight();
}

function refreshDrawHighlight() {
  // Update individual cell classes (for hover/cursor)
  document.querySelectorAll(".grid-empty-cell").forEach(el => {
    const key = `${el.dataset.col},${el.dataset.row}`;
    el.classList.toggle("paint-selected", draw.cells.has(key));
  });
  redrawShapeOverlay();
}

function redrawShapeOverlay() {
  document.querySelectorAll(".draw-cell-overlay").forEach(el => el.remove());
  if (draw.cells.size === 0) return;

  const grid = document.getElementById("floorPlanGrid");
  if (!grid) return;
  const gridRect = grid.getBoundingClientRect();
  const GAP = 5;

  draw.cells.forEach(key => {
    const [c, r] = key.split(",").map(Number);
    const cellEl = grid.querySelector(`.grid-empty-cell[data-col="${c}"][data-row="${r}"]`);
    if (!cellEl) return;

    const cr = cellEl.getBoundingClientRect();
    const hasTop    = draw.cells.has(`${c},${r - 1}`);
    const hasBottom = draw.cells.has(`${c},${r + 1}`);
    const hasLeft   = draw.cells.has(`${c - 1},${r}`);
    const hasRight  = draw.cells.has(`${c + 1},${r}`);

    const eT = hasTop    ? GAP : 0;
    const eB = hasBottom ? GAP : 0;
    const eL = hasLeft   ? GAP : 0;
    const eR = hasRight  ? GAP : 0;

    const div = document.createElement("div");
    div.className = "draw-cell-overlay";
    div.style.left   = (cr.left - gridRect.left - eL) + "px";
    div.style.top    = (cr.top  - gridRect.top  - eT) + "px";
    div.style.width  = (cr.width  + eL + eR) + "px";
    div.style.height = (cr.height + eT + eB) + "px";

    const tl = (!hasTop && !hasLeft)     ? "6px" : "0";
    const tr = (!hasTop && !hasRight)    ? "6px" : "0";
    const br = (!hasBottom && !hasRight) ? "6px" : "0";
    const bl = (!hasBottom && !hasLeft)  ? "6px" : "0";
    div.style.borderRadius = `${tl} ${tr} ${br} ${bl}`;
    div.style.borderTop    = hasTop    ? "none" : "2px solid rgba(15,108,95,0.85)";
    div.style.borderBottom = hasBottom ? "none" : "2px solid rgba(15,108,95,0.85)";
    div.style.borderLeft   = hasLeft   ? "none" : "2px solid rgba(15,108,95,0.85)";
    div.style.borderRight  = hasRight  ? "none" : "2px solid rgba(15,108,95,0.85)";

    grid.appendChild(div);
  });
}

function drawShopShapeOverlay(shop, isRented) {
  const grid = document.getElementById("floorPlanGrid");
  if (!grid) return;
  const gridRect = grid.getBoundingClientRect();
  const GAP = 5;

  const colors = {
    RENTED:       { border: "#a0c8e8" },
    OPEN:         { border: "#a8dfc0" },
    CLOSED:       { border: "#f0b8b8" },
    UNDER_REPAIR: { border: "#f0d898" },
  };
  const status = isRented ? "RENTED" : shop.status;
  const col = colors[status] || colors.OPEN;
  const cellSet = new Set(shop.cells);

  cellSet.forEach(key => {
    const [c, r] = key.split(",").map(Number);
    const cellEls = grid.querySelectorAll(`.floor-cell[data-shop-id="${shop.shopId}"]`);
    let cellEl = null;
    cellEls.forEach(el => {
      const colStr = el.style.gridColumn;
      const rowStr = el.style.gridRow;
      if (colStr && rowStr) {
        const col_ = parseInt(colStr) - 1;
        const row_ = parseInt(rowStr) - 1;
        if (col_ === c && row_ === r) cellEl = el;
      }
    });
    if (!cellEl) return;

    const cr = cellEl.getBoundingClientRect();
    // A neighbour direction should have NO border if:
    // - the neighbour is also in the cell list (shared interior edge), OR
    // - the neighbour is a hole (inside bounding box but not in cell list) — don't frame holes
    const isInteriorOrHole = (nc, nr) => {
      if (cellSet.has(`${nc},${nr}`)) return true; // shared with another cell
      // Check if it's a hole (inside bounding box)
      if (nc >= shop.posX && nc < shop.posX + shop.width &&
          nr >= shop.posY && nr < shop.posY + shop.height) return true;
      return false;
    };
    const hasTop    = isInteriorOrHole(c, r - 1);
    const hasBottom = isInteriorOrHole(c, r + 1);
    const hasLeft   = isInteriorOrHole(c - 1, r);
    const hasRight  = isInteriorOrHole(c + 1, r);

    const eT = hasTop    ? GAP : 0;
    const eB = hasBottom ? GAP : 0;
    const eL = hasLeft   ? GAP : 0;
    const eR = hasRight  ? GAP : 0;

    const div = document.createElement("div");
    div.className = "shop-shape-overlay";
    div.style.left   = (cr.left - gridRect.left - eL) + "px";
    div.style.top    = (cr.top  - gridRect.top  - eT) + "px";
    div.style.width  = (cr.width  + eL + eR) + "px";
    div.style.height = (cr.height + eT + eB) + "px";
    div.style.background = "transparent"; // cell itself shows the status colour
    div.style.borderTop    = hasTop    ? "none" : `2px solid ${col.border}`;
    div.style.borderBottom = hasBottom ? "none" : `2px solid ${col.border}`;
    div.style.borderLeft   = hasLeft   ? "none" : `2px solid ${col.border}`;
    div.style.borderRight  = hasRight  ? "none" : `2px solid ${col.border}`;
    div.style.borderRadius = `${(!hasTop&&!hasLeft)?"6px":"0"} ${(!hasTop&&!hasRight)?"6px":"0"} ${(!hasBottom&&!hasRight)?"6px":"0"} ${(!hasBottom&&!hasLeft)?"6px":"0"}`;
    grid.appendChild(div);
  });
}

// Close popover button
document.getElementById("shopPopoverClose").addEventListener("click", () => {
  document.getElementById("shopInfoCard").classList.add("hidden");
  selectedShopId = null;
  renderFloorPlan();
});

// Dismiss popover when clicking outside it and outside the grid
document.addEventListener("click", e => {
  const card = document.getElementById("shopInfoCard");
  if (card.classList.contains("hidden")) return;
  if (card.contains(e.target)) return;
  if (e.target.closest(".floor-cell") || e.target.closest(".grid-empty-cell")) return;
  card.classList.add("hidden");
  selectedShopId = null;
  renderFloorPlan();
});

// Reposition popover on scroll/resize
window.addEventListener("resize", () => {
  const card = document.getElementById("shopInfoCard");
  if (!card.classList.contains("hidden")) card.classList.add("hidden");
});

// Draw mode toggle button
document.getElementById("btnDrawMode").addEventListener("click", () => {
  draw.active = !draw.active;
  draw.cells.clear();
  draw.painting = false;
  document.getElementById("drawToolbar").classList.toggle("hidden", !draw.active);
  document.getElementById("btnDrawMode").textContent = draw.active ? "✕ Cancel Draw" : "✏️ Draw Shop";
  document.getElementById("shopInfoCard").classList.add("hidden");
  selectedShopId = null;
  renderFloorPlan();
});

document.getElementById("btnDrawCancel").addEventListener("click", () => {
  draw.active = false;
  draw.cells.clear();
  document.getElementById("drawToolbar").classList.add("hidden");
  document.getElementById("btnDrawMode").textContent = "✏️ Draw Shop";
  renderFloorPlan();
});

document.getElementById("btnDrawConfirm").addEventListener("click", async () => {
  if (draw.cells.size === 0) { setMsg("Paint at least one cell first.", true); return; }
  const num  = document.getElementById("drawShopNum").value.trim();
  const area = parseFloat(document.getElementById("drawShopArea").value);
  const type = document.getElementById("drawShopType").value;
  if (!num)        { setMsg("Shop number is required.", true); return; }
  if (!(area > 0)) { setMsg("Area must be greater than 0.", true); return; }

  // Compute bounding box for fallback, but send the full cell list
  const cols = [...draw.cells].map(k => parseInt(k.split(",")[0]));
  const rows = [...draw.cells].map(k => parseInt(k.split(",")[1]));
  const minC = Math.min(...cols), maxC = Math.max(...cols);
  const minR = Math.min(...rows), maxR = Math.max(...rows);

  try {
    await api("POST", "/shops", {
      shopNum: num, area, type, status: "OPEN",
      posX: minC, posY: minR,
      width:  maxC - minC + 1,
      height: maxR - minR + 1,
      cells: [...draw.cells],
      floor: currentFloor || "G",
    });
    draw.active = false;
    draw.cells.clear();
    document.getElementById("drawToolbar").classList.add("hidden");
    document.getElementById("btnDrawMode").textContent = "✏️ Draw Shop";
    document.getElementById("drawShopNum").value  = "";
    document.getElementById("drawShopArea").value = "";
    await refreshState();
    setMsg(`Shop ${num} added.`);
  } catch (err) { setMsg(err.message, true); }
});

function showShopInfoCard(shop, effectiveStatus, anchorRect) {
  const card = document.getElementById("shopInfoCard");
  document.getElementById("shopInfoTitle").textContent = "Shop " + shop.shopNum;

  const contract = state.contracts.find(c => c.shopId === shop.shopId && c.status === "ACTIVE");
  const pending  = state.contracts.find(c => c.shopId === shop.shopId && c.status === "PENDING_APPROVAL");

  const rows = [
    ["Shop No.", shop.shopNum],
    ["Type", badge(shop.type)],
    ["Status", badge(effectiveStatus === "RENTED" ? "ACTIVE" : shop.status)],
    ["Area", shop.area + " m²"],
    ["Position", `col ${shop.posX}, row ${shop.posY} (${shop.width}×${shop.height})`],
  ];
  if (contract) { rows.push(["Tenant", contract.tenant], ["Base Rent", "HKD " + fmt(contract.baseRent)]); }

  document.getElementById("shopInfoBody").innerHTML = rows.map(([l, v]) =>
    `<div class="shop-info-body-row"><span class="label">${l}</span><span>${v}</span></div>`
  ).join("");

  const actions = document.getElementById("shopInfoActions");
  actions.innerHTML = "";

  if (currentUser.role === "tenant" && effectiveStatus === "OPEN") {
    const btn = document.createElement("button");
    btn.className = "btn btn-primary";
    btn.textContent = "Request Lease";
    btn.addEventListener("click", () => openLeaseModal(shop));
    actions.appendChild(btn);
  }

  if (currentUser.role === "contractmanager") {
    if (pending) {
      const btn = document.createElement("button");
      btn.className = "btn btn-primary";
      btn.textContent = `Approve Contract #${pending.contractId}`;
      btn.addEventListener("click", async () => {
        try {
          await api("POST", "/manager/approve-contract", { contractId: pending.contractId });
          await refreshState();
          setMsg(`Contract #${pending.contractId} approved.`);
        } catch (err) { setMsg(err.message, true); }
      });
      actions.appendChild(btn);
    }
    if (contract) {
      const btn = document.createElement("button");
      btn.className = "btn btn-danger";
      btn.textContent = `Terminate Contract #${contract.contractId}`;
      btn.addEventListener("click", async () => {
        if (!confirm("Terminate this contract?")) return;
        try {
          await api("POST", "/manager/terminate-contract", { contractId: contract.contractId });
          await refreshState();
          setMsg(`Contract #${contract.contractId} terminated.`);
        } catch (err) { setMsg(err.message, true); }
      });
      actions.appendChild(btn);
    }
  }

  // Place off-screen first, show, then position after layout
  card.style.left = "-9999px";
  card.style.top  = "0px";
  card.classList.remove("hidden");
  requestAnimationFrame(() => positionPopover(card, anchorRect));
}

function positionPopover(card, anchorRect) {
  const MARGIN = 12;
  const POPOVER_W = 268;
  const cardH = card.offsetHeight;
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  let left, top;

  if (anchorRect) {
    // Try right of cell first, then left
    if (anchorRect.right + MARGIN + POPOVER_W <= vw - MARGIN) {
      left = anchorRect.right + MARGIN;
    } else {
      left = anchorRect.left - POPOVER_W - MARGIN;
    }
    top = anchorRect.top;
  } else {
    left = vw / 2 - POPOVER_W / 2;
    top  = vh / 2 - cardH / 2;
  }

  // Clamp within viewport
  left = Math.max(MARGIN, Math.min(vw - POPOVER_W - MARGIN, left));
  top  = Math.max(MARGIN, Math.min(vh - cardH - MARGIN, top));

  card.style.left = left + "px";
  card.style.top  = top  + "px";
  card.style.visibility = "";
}

// ── Lease Modal ───────────────────────────────────────────────────────────────

function openLeaseModal(shop) {
  document.getElementById("leaseModalShopInfo").innerHTML =
    `Shop: <strong>${shop.shopNum}</strong> &nbsp;|&nbsp; Type: ${shop.type} &nbsp;|&nbsp; Area: ${shop.area} m²`;
  document.getElementById("leaseBaseRent").value = "";
  document.getElementById("leaseModal").classList.remove("hidden");
  document.getElementById("leaseModal")._shopId = shop.shopId;
}

document.getElementById("btnCloseLeaseModal").addEventListener("click", () => {
  document.getElementById("leaseModal").classList.add("hidden");
});

document.getElementById("leaseForm").addEventListener("submit", async e => {
  e.preventDefault();
  const shopId = document.getElementById("leaseModal")._shopId;
  const baseRent = parseFloat(document.getElementById("leaseBaseRent").value);
  try {
    const data = await api("POST", "/tenant/request-lease", { username: currentUser.username, shopId, baseRent });
    document.getElementById("leaseModal").classList.add("hidden");
    await refreshState();
    setMsg(`Lease request submitted. Contract ID: ${data.contract.contractId}`);
  } catch (err) { setMsg(err.message, true); }
});

// ── Tenant Panels ─────────────────────────────────────────────────────────────

function renderTenantContracts() {
  const myContracts = state.contracts.filter(c => c.tenant === currentUser.username);
  const el = document.getElementById("tenantContractList");
  if (!myContracts.length) { el.innerHTML = "<p class='empty'>No contracts yet.</p>"; return; }
  const activeId = state.activeContractIdByUser?.[currentUser.username];
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop</th><th>Base Rent</th><th>Commission</th><th>Status</th><th>Action</th></tr></thead>
    <tbody>${myContracts.map(c => `
      <tr>
        <td>${c.contractId}${activeId === c.contractId ? ' <span class="badge ACTIVE">Active</span>' : ''}</td>
        <td>${c.shopNum}</td>
        <td>HKD ${fmt(c.baseRent)}</td>
        <td>${(c.commissionRate * 100).toFixed(1)}%</td>
        <td>${badge(c.status)}</td>
        <td><button class="btn btn-xs btn-ghost" onclick="setActiveContract(${c.contractId})">Set Active</button></td>
      </tr>`).join("")}
    </tbody></table>`;
}

async function setActiveContract(contractId) {
  try {
    await api("POST", "/tenant/active-contract", { username: currentUser.username, contractId });
    await refreshState();
    setMsg("Active contract updated.");
  } catch (err) { setMsg(err.message, true); }
}

function renderActiveTenantContractInfo() {
  const el = document.getElementById("activeTenantContractInfo");
  const activeId = state.activeContractIdByUser?.[currentUser.username];
  if (!activeId) { el.textContent = "No active contract selected. Go to My Contracts and click Set Active."; return; }
  const c = state.contracts.find(c => c.contractId === activeId);
  if (!c) { el.textContent = "Active contract not found."; return; }
  el.innerHTML = `Active contract: <strong>#${c.contractId}</strong> &nbsp;|&nbsp; Shop: ${c.shopNum} &nbsp;|&nbsp; Status: ${badge(c.status)} &nbsp;|&nbsp; Base Rent: HKD ${fmt(c.baseRent)}`;
}

function renderTenantPayments() {
  const myContractIds = new Set(state.contracts.filter(c => c.tenant === currentUser.username).map(c => c.contractId));
  const myPayments = state.payments.filter(p => myContractIds.has(p.contractId));
  const el = document.getElementById("tenantPaymentList");
  if (!myPayments.length) { el.innerHTML = "<p class='empty'>No payments yet.</p>"; return; }
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Month</th><th>Sales</th><th>Amount Due</th><th>Status</th></tr></thead>
    <tbody>${myPayments.map(p => `
      <tr>
        <td>${p.paymentId}</td>
        <td>${p.month || "-"}</td>
        <td>HKD ${fmt(p.sales)}</td>
        <td>HKD ${fmt(p.amount)}</td>
        <td>${badge(p.status)}</td>
      </tr>`).join("")}
    </tbody></table>`;
}

document.getElementById("salesForm").addEventListener("submit", async e => {
  e.preventDefault();
  const month = document.getElementById("salesMonth").value;
  const sales = parseFloat(document.getElementById("salesAmount").value);
  try {
    const data = await api("POST", "/tenant/upload-sales", { username: currentUser.username, month, sales });
    await refreshState();
    setMsg(`Payment generated. ID: ${data.payment.paymentId}, Amount: HKD ${fmt(data.payment.amount)}`);
    document.getElementById("salesForm").reset();
  } catch (err) { setMsg(err.message, true); }
});

// ── Manager Panels ────────────────────────────────────────────────────────────

function renderManagerContracts() {
  const el = document.getElementById("managerContractList");
  if (!state.contracts.length) { el.innerHTML = "<p class='empty'>No contracts.</p>"; return; }
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Tenant</th><th>Shop</th><th>Base Rent</th><th>Status</th><th>Actions</th></tr></thead>
    <tbody>${state.contracts.map(c => `
      <tr>
        <td>${c.contractId}</td>
        <td>${c.tenant}</td>
        <td>${c.shopNum}</td>
        <td>HKD ${fmt(c.baseRent)}</td>
        <td>${badge(c.status)}</td>
        <td>
          ${c.status === "PENDING_APPROVAL" ? `<button class="btn btn-xs btn-primary" onclick="approveContract(${c.contractId})">Approve</button>` : ""}
          ${c.status === "ACTIVE" || c.status === "PENDING_APPROVAL" ? `<button class="btn btn-xs btn-danger" onclick="terminateContract(${c.contractId})">Terminate</button>` : ""}
        </td>
      </tr>`).join("")}
    </tbody></table>`;
}

async function approveContract(contractId) {
  try {
    await api("POST", "/manager/approve-contract", { contractId });
    await refreshState();
    setMsg(`Contract #${contractId} approved.`);
  } catch (err) { setMsg(err.message, true); }
}

async function terminateContract(contractId) {
  if (!confirm("Terminate this contract?")) return;
  try {
    await api("POST", "/manager/terminate-contract", { contractId });
    await refreshState();
    setMsg(`Contract #${contractId} terminated.`);
  } catch (err) { setMsg(err.message, true); }
}

function renderManagerShops() {
  // Keep floor select in sync whenever this panel renders
  populateFloorSelect("shopFormFloor", document.getElementById("shopFormFloor").value || currentFloor || (state.floors[0] || "G"));

  const el = document.getElementById("managerShopList");
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop No.</th><th>Type</th><th>Area</th><th>Status</th><th>Actions</th></tr></thead>
    <tbody>${state.shops.map(s => `
      <tr>
        <td>${s.shopId}</td>
        <td>${s.shopNum}</td>
        <td>${badge(s.type)}</td>
        <td>${s.area} m²</td>
        <td>${badge(s.status)}</td>
        <td>
          <button class="btn btn-xs btn-ghost" onclick="editShopForm(${s.shopId})">Edit</button>
          <button class="btn btn-xs btn-danger" onclick="deleteShop(${s.shopId})">Delete</button>
        </td>
      </tr>`).join("")}
    </tbody></table>`;
}

document.getElementById("btnAddShop").addEventListener("click", () => {
  document.getElementById("shopFormId").value = "";
  document.getElementById("shopFormTitle").textContent = "Add Shop";
  document.getElementById("shopForm").reset();
  populateFloorSelect("shopFormFloor", currentFloor || (state.floors[0] || "G"));
  document.getElementById("shopFormWrap").classList.remove("hidden");
});

document.getElementById("btnCancelShopForm").addEventListener("click", () => {
  document.getElementById("shopFormWrap").classList.add("hidden");
});

function editShopForm(shopId) {
  const shop = state.shops.find(s => s.shopId === shopId);
  if (!shop) return;
  document.getElementById("shopFormId").value = shopId;
  document.getElementById("shopFormTitle").textContent = "Edit Shop";
  document.getElementById("shopFormNum").value = shop.shopNum;
  document.getElementById("shopFormArea").value = shop.area;
  document.getElementById("shopFormType").value = shop.type;
  document.getElementById("shopFormStatus").value = shop.status;
  populateFloorSelect("shopFormFloor", shop.floor || "G");
  document.getElementById("shopFormPosX").value = shop.posX;
  document.getElementById("shopFormPosY").value = shop.posY;
  document.getElementById("shopFormWidth").value = shop.width;
  document.getElementById("shopFormHeight").value = shop.height;
  document.getElementById("shopFormWrap").classList.remove("hidden");
  document.getElementById("shopFormWrap").scrollIntoView({ behavior: "smooth" });
}

function populateFloorSelect(selectId, selectedFloor) {
  const sel = document.getElementById(selectId);
  if (!sel) return;
  const floors = state.floors && state.floors.length > 0 ? state.floors : ["G"];
  // Preserve current value if it's still valid, otherwise use selectedFloor
  const current = sel.value && floors.includes(sel.value) ? sel.value : (selectedFloor || floors[0]);
  sel.innerHTML = floors.map(f =>
    `<option value="${f}"${f === current ? " selected" : ""}>${f}</option>`
  ).join("");
}

document.getElementById("shopForm").addEventListener("submit", async e => {
  e.preventDefault();
  const id = document.getElementById("shopFormId").value;
  const body = {
    shopNum: document.getElementById("shopFormNum").value,
    area: parseFloat(document.getElementById("shopFormArea").value),
    type: document.getElementById("shopFormType").value,
    status: document.getElementById("shopFormStatus").value,
    floor: document.getElementById("shopFormFloor").value,
    posX: parseInt(document.getElementById("shopFormPosX").value) || 0,
    posY: parseInt(document.getElementById("shopFormPosY").value) || 0,
    width: parseInt(document.getElementById("shopFormWidth").value) || 2,
    height: parseInt(document.getElementById("shopFormHeight").value) || 2,
  };
  try {
    if (id) await api("PUT", `/shops/${id}`, body);
    else await api("POST", "/shops", body);
    await refreshState();
    document.getElementById("shopFormWrap").classList.add("hidden");
    setMsg(id ? "Shop updated." : "Shop added.");
  } catch (err) { setMsg(err.message, true); }
});

async function deleteShop(shopId) {
  if (!confirm("Delete this shop?")) return;
  try {
    await api("DELETE", `/shops/${shopId}`);
    await refreshState();
    setMsg("Shop deleted.");
  } catch (err) { setMsg(err.message, true); }
}

// ── Floor Management (CM) ─────────────────────────────────────────────────────

function renderManagerFloors() {
  const el = document.getElementById("floorList");
  const floors = state.floors || [];
  if (!floors.length) { el.innerHTML = "<p class='empty'>No floors defined.</p>"; return; }
  el.innerHTML = `<table>
    <thead><tr><th>Floor</th><th>Shops</th><th>Actions</th></tr></thead>
    <tbody>${floors.map(f => {
      const count = state.shops.filter(s => (s.floor || "G") === f).length;
      return `<tr>
        <td><strong>${f}</strong></td>
        <td>${count}</td>
        <td>
          <button class="btn btn-xs btn-ghost" onclick="renameFloor('${f}')">Rename</button>
          <button class="btn btn-xs btn-danger" onclick="deleteFloor('${f}')">Delete</button>
        </td>
      </tr>`;
    }).join("")}
    </tbody></table>`;
}

async function renameFloor(oldName) {
  const newName = prompt(`Rename floor "${oldName}" to:`, oldName);
  if (!newName || newName === oldName) return;
  try {
    await api("PUT", `/floors/${encodeURIComponent(oldName)}`, { name: newName });
    if (currentFloor === oldName) currentFloor = newName;
    await refreshState();
    setMsg(`Floor renamed to "${newName}".`);
  } catch (err) { setMsg(err.message, true); }
}

async function deleteFloor(name) {
  if (!confirm(`Delete floor "${name}"? All shops on this floor must be removed first.`)) return;
  try {
    await api("DELETE", `/floors/${encodeURIComponent(name)}`);
    if (currentFloor === name) currentFloor = null;
    await refreshState();
    setMsg(`Floor "${name}" deleted.`);
  } catch (err) { setMsg(err.message, true); }
}

document.getElementById("addFloorForm").addEventListener("submit", async e => {
  e.preventDefault();
  const name = document.getElementById("addFloorName").value.trim();
  if (!name) return;
  try {
    await api("POST", "/floors", { name });
    document.getElementById("addFloorName").value = "";
    await refreshState();
    setMsg(`Floor "${name}" added.`);
  } catch (err) { setMsg(err.message, true); }
});

// ── Accounting Panels ─────────────────────────────────────────────────────────

function renderAccountingPayments() {
  const el = document.getElementById("accountingPaymentList");
  if (!state.payments.length) { el.innerHTML = "<p class='empty'>No payments.</p>"; return; }
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Contract</th><th>Month</th><th>Sales</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead>
    <tbody>${state.payments.map(p => `
      <tr>
        <td>${p.paymentId}</td>
        <td>#${p.contractId}</td>
        <td>${p.month || "-"}</td>
        <td>HKD ${fmt(p.sales)}</td>
        <td>HKD ${fmt(p.amount)}</td>
        <td>${badge(p.status)}</td>
        <td>${p.status === "UNVERIFIED" ? `<button class="btn btn-xs btn-primary" onclick="confirmPayment(${p.paymentId})">Confirm</button>` : ""}</td>
      </tr>`).join("")}
    </tbody></table>`;
}

async function confirmPayment(paymentId) {
  try {
    await api("POST", "/accounting/confirm-payment", { paymentId });
    await refreshState();
    setMsg(`Payment #${paymentId} confirmed.`);
  } catch (err) { setMsg(err.message, true); }
}

function renderAccountingInspect() {
  const rentedIds = new Set(state.contracts.filter(c => c.status === "ACTIVE").map(c => c.shopId));
  const el = document.getElementById("accountingShopList");
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop No.</th><th>Type</th><th>Area</th><th>Status</th><th>Tenant</th><th>Base Rent</th></tr></thead>
    <tbody>${state.shops.map(s => {
      const c = state.contracts.find(c => c.shopId === s.shopId && c.status === "ACTIVE");
      return `<tr>
        <td>${s.shopId}</td><td>${s.shopNum}</td><td>${badge(s.type)}</td>
        <td>${s.area} m²</td>
        <td>${badge(rentedIds.has(s.shopId) ? "ACTIVE" : s.status)}</td>
        <td>${c ? c.tenant : "-"}</td>
        <td>${c ? "HKD " + fmt(c.baseRent) : "-"}</td>
      </tr>`;
    }).join("")}
    </tbody></table>`;
}

// ── Admin Panels ──────────────────────────────────────────────────────────────

function renderAdminUsers() {
  const el = document.getElementById("adminUserList");
  el.innerHTML = `<table>
    <thead><tr><th>Username</th><th>Role</th><th>User ID</th><th>Actions</th></tr></thead>
    <tbody>${state.users.map(u => `
      <tr>
        <td>${u.username}</td>
        <td>${badge(u.role)}</td>
        <td>${u.userId}</td>
        <td>
          ${u.role !== "systemadmin" ? `<button class="btn btn-xs btn-danger" onclick="deleteUser('${u.username}')">Delete</button>` : ""}
          <button class="btn btn-xs btn-ghost" onclick="promptChangePassword('${u.username}')">Change Password</button>
          <button class="btn btn-xs btn-ghost" onclick="promptSetPermission('${u.userId}')">Set Permission</button>
        </td>
      </tr>`).join("")}
    </tbody></table>`;
}

async function deleteUser(username) {
  if (!confirm(`Delete user ${username}?`)) return;
  try {
    await api("DELETE", `/admin/users/${encodeURIComponent(username)}`);
    await refreshState();
    setMsg(`User ${username} deleted.`);
  } catch (err) { setMsg(err.message, true); }
}

async function promptChangePassword(username) {
  const pw = prompt(`New password for ${username}:`);
  if (!pw) return;
  try {
    await api("PUT", `/admin/users/${encodeURIComponent(username)}/password`, { password: pw });
    setMsg(`Password updated for ${username}.`);
  } catch (err) { setMsg(err.message, true); }
}

async function promptSetPermission(userId) {
  const perm = prompt(`Set permission for user ${userId}:`);
  if (perm === null) return;
  try {
    await api("PUT", `/admin/permissions/${encodeURIComponent(userId)}`, { permission: perm });
    await refreshState();
    setMsg("Permission updated.");
  } catch (err) { setMsg(err.message, true); }
}

document.getElementById("btnAddUser").addEventListener("click", () => {
  document.getElementById("userForm").reset();
  document.getElementById("userFormWrap").classList.remove("hidden");
});

document.getElementById("btnCancelUserForm").addEventListener("click", () => {
  document.getElementById("userFormWrap").classList.add("hidden");
});

document.getElementById("userForm").addEventListener("submit", async e => {
  e.preventDefault();
  const role = document.getElementById("userFormRole").value;
  const username = document.getElementById("userFormUsername").value.trim();
  const password = document.getElementById("userFormPassword").value.trim();
  try {
    await api("POST", "/admin/users", { role, username, password });
    await refreshState();
    document.getElementById("userFormWrap").classList.add("hidden");
    setMsg(`User ${username} created.`);
  } catch (err) { setMsg(err.message, true); }
});

function renderAdminShops() {
  const el = document.getElementById("adminShopList");
  el.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop No.</th><th>Type</th><th>Area</th><th>Status</th><th>Position</th></tr></thead>
    <tbody>${state.shops.map(s => `
      <tr>
        <td>${s.shopId}</td><td>${s.shopNum}</td><td>${badge(s.type)}</td>
        <td>${s.area} m²</td><td>${badge(s.status)}</td>
        <td>(${s.posX},${s.posY}) ${s.width}x${s.height}</td>
      </tr>`).join("")}
    </tbody></table>`;
}

// ── Audit Log ─────────────────────────────────────────────────────────────────

function renderAuditLog() {
  const el = document.getElementById("auditLogList");
  const logs = state.logs || [];
  if (!logs.length) { el.innerHTML = "<p class='empty' style='padding:8px'>No activity yet.</p>"; return; }
  el.innerHTML = logs.slice(0, 60).map(l => `<div class="log-entry">${l}</div>`).join("");
}
