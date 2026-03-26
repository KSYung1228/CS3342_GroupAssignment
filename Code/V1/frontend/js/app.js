const API_BASE = "http://localhost:8080/api";

let state = emptyState();
let currentUser = null;
let activePanel = null;

const rolePanels = {
  tenant: [
    "tenantShops",
    "tenantContracts",
    "tenantSales",
    "tenantPayments",
    "auditLogPanel",
  ],
  contractmanager: ["managerContracts", "managerShops", "auditLogPanel"],
  accounting: ["accountingPayments", "accountingInspect", "auditLogPanel"],
  systemadmin: ["adminUsers", "adminShops", "auditLogPanel"],
};

function emptyState() {
  return {
    users: [],
    permissions: {},
    shops: [],
    contracts: [],
    payments: [],
    activeContractIdByUser: {},
    logs: [],
  };
}

async function api(method, path, body) {
  const opts = {
    method,
    headers: {
      "Content-Type": "application/json",
    },
  };
  if (body !== undefined) {
    opts.body = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, opts);
  } catch {
    throw new Error("Cannot connect to Java API. Start the server first.");
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.error || `Request failed (${res.status})`);
  }
  return data;
}

async function refreshState() {
  const data = await api("GET", "/state");
  state = {
    ...emptyState(),
    ...data,
  };
  renderAll();
}

function setMessage(text, isError = false) {
  const el = document.getElementById("appMessage");
  el.textContent = text;
  el.style.color = isError ? "#ba3c3c" : "#0a4f45";
}

function setAuthMessage(text, isError = false) {
  const el = document.getElementById("authMessage");
  el.textContent = text;
  el.style.color = isError ? "#ba3c3c" : "#0a4f45";
}

function showView(id) {
  document
    .querySelectorAll(".view")
    .forEach((v) => v.classList.remove("active"));
  document.getElementById(id).classList.add("active");
}

function makeBadge(value) {
  return `<span class="badge ${value}">${value}</span>`;
}

function fmt(n) {
  return Number(n || 0).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function getUserRoleLabel(role) {
  if (role === "systemadmin") return "System Admin";
  if (role === "contractmanager") return "Contract Manager";
  if (role === "accounting") return "Accounting";
  if (role === "tenant") return "Tenant";
  return role;
}

function panelTitle(panelId) {
  const map = {
    tenantShops: "Open Shops",
    tenantContracts: "My Contracts",
    tenantSales: "Upload Sales",
    tenantPayments: "My Payments",
    managerContracts: "Contract Queue",
    managerShops: "Shop Status",
    accountingPayments: "Verify Payments",
    accountingInspect: "Inspect Shops",
    adminUsers: "Users",
    adminShops: "Shops",
    auditLogPanel: "Activity",
  };
  return map[panelId] || panelId;
}

function buildNav() {
  const nav = document.getElementById("roleNav");
  nav.innerHTML = "";
  if (!currentUser) {
    return;
  }

  const panels = rolePanels[currentUser.role] || [];
  panels.forEach((panelId) => {
    const btn = document.createElement("button");
    btn.textContent = panelTitle(panelId);
    btn.classList.toggle("active", panelId === activePanel);
    btn.addEventListener("click", () => showPanel(panelId));
    nav.appendChild(btn);
  });
}

function showPanel(panelId) {
  activePanel = panelId;
  document.querySelectorAll(".panel").forEach((p) => p.classList.add("hidden"));
  const target = document.getElementById(panelId);
  if (target) {
    target.classList.remove("hidden");
  }
  buildNav();
  renderAll();
}

function renderAll() {
  renderTenantShops();
  renderTenantContracts();
  renderTenantPayments();
  renderManagerContracts();
  renderManagerShops();
  renderAccountingPayments();
  renderInspect();
  renderAdminUsers();
  renderAdminShops();
  renderLog();
}

function renderTenantShops() {
  const box = document.getElementById("tenantShopList");
  box.innerHTML = "";
  if (!currentUser || currentUser.role !== "tenant") {
    return;
  }

  const openShops = state.shops.filter((s) => s.status === "OPEN");
  if (!openShops.length) {
    box.innerHTML = '<p class="hint">No open shops available.</p>';
    return;
  }

  openShops.forEach((shop) => {
    const card = document.createElement("div");
    card.className = "card";
    card.innerHTML = `
      <h4>${shop.shopNum}</h4>
      <p class="kv">Type: ${shop.type}</p>
      <p class="kv">Area: ${shop.area} sq ft</p>
      <p class="kv">Status: ${makeBadge(shop.status)}</p>
      <label>Base Rent
        <input id="rent-${shop.shopId}" type="number" min="0" step="0.01" placeholder="Enter rent" />
      </label>
      <button class="btn btn-primary" type="button" data-shop-id="${shop.shopId}">Request Lease</button>
    `;
    card.querySelector("button").addEventListener("click", () => {
      const value = document.getElementById(`rent-${shop.shopId}`).value;
      requestLease(shop.shopId, Number(value));
    });
    box.appendChild(card);
  });
}

function renderTenantContracts() {
  const box = document.getElementById("tenantContractList");
  if (!currentUser || currentUser.role !== "tenant") {
    box.innerHTML = "";
    return;
  }

  const mine = state.contracts.filter(
    (c) => c.tenantUsername === currentUser.username,
  );
  if (!mine.length) {
    box.innerHTML = '<p class="hint">No contracts yet.</p>';
    return;
  }

  const activeId = state.activeContractIdByUser[currentUser.username];
  box.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Shop</th><th>Status</th><th>Base Rent</th><th>Action</th></tr></thead>
    <tbody>
      ${mine
        .map((c) => {
          const shop = state.shops.find((s) => s.shopId === c.shopId);
          const isActive = activeId === c.contractId;
          return `<tr>
            <td>${c.contractId}</td>
            <td>${shop ? shop.shopNum : c.shopId}</td>
            <td>${makeBadge(c.status)}</td>
            <td>${fmt(c.baseRent)}</td>
            <td>
              <button class="btn ${isActive ? "btn-ok" : "btn-ghost"}" type="button" onclick="setActiveContract(${c.contractId})">${isActive ? "Active" : "Set Active"}</button>
            </td>
          </tr>`;
        })
        .join("")}
    </tbody>
  </table>`;
}

function renderTenantPayments() {
  const box = document.getElementById("tenantPaymentList");
  if (!currentUser || currentUser.role !== "tenant") {
    box.innerHTML = "";
    return;
  }

  const mineContracts = new Set(
    state.contracts
      .filter((c) => c.tenantUsername === currentUser.username)
      .map((c) => c.contractId),
  );
  const minePayments = state.payments.filter((p) =>
    mineContracts.has(p.contractId),
  );

  if (!minePayments.length) {
    box.innerHTML = '<p class="hint">No payment records.</p>';
    return;
  }

  box.innerHTML = `<table>
    <thead><tr><th>Payment ID</th><th>Contract</th><th>Month</th><th>Sales</th><th>Amount</th><th>Status</th></tr></thead>
    <tbody>
      ${minePayments
        .map(
          (p) => `<tr>
            <td>${p.paymentId}</td>
            <td>${p.contractId}</td>
            <td>${p.month || "N/A"}</td>
            <td>${fmt(p.sales)}</td>
            <td>${fmt(p.amount)}</td>
            <td>${makeBadge(p.status)}</td>
          </tr>`,
        )
        .join("")}
    </tbody>
  </table>`;
}

function renderManagerContracts() {
  const box = document.getElementById("managerContractList");
  if (!currentUser || currentUser.role !== "contractmanager") {
    box.innerHTML = "";
    return;
  }

  if (!state.contracts.length) {
    box.innerHTML = '<p class="hint">No contracts in queue.</p>';
    return;
  }

  box.innerHTML = `<table>
    <thead><tr><th>ID</th><th>Tenant</th><th>Shop</th><th>Status</th><th>Base Rent</th><th>Action</th></tr></thead>
    <tbody>
      ${state.contracts
        .map((c) => {
          const shop = state.shops.find((s) => s.shopId === c.shopId);
          return `<tr>
            <td>${c.contractId}</td>
            <td>${c.tenantUsername}</td>
            <td>${shop ? shop.shopNum : c.shopId}</td>
            <td>${makeBadge(c.status)}</td>
            <td>${fmt(c.baseRent)}</td>
            <td>
              ${c.status === "PENDING_APPROVAL" ? `<button class="btn btn-ok" type="button" onclick="approveContract(${c.contractId})">Approve</button>` : "-"}
            </td>
          </tr>`;
        })
        .join("")}
    </tbody>
  </table>`;
}

function renderManagerShops() {
  const box = document.getElementById("managerShopList");
  if (!currentUser || currentUser.role !== "contractmanager") {
    box.innerHTML = "";
    return;
  }

  box.innerHTML = renderShopStatusTable();
}

function renderAccountingPayments() {
  const box = document.getElementById("accountingPaymentList");
  if (!currentUser || currentUser.role !== "accounting") {
    box.innerHTML = "";
    return;
  }

  if (!state.payments.length) {
    box.innerHTML = '<p class="hint">No payments yet.</p>';
    return;
  }

  box.innerHTML = `<table>
    <thead><tr><th>Payment ID</th><th>Contract</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead>
    <tbody>
      ${state.payments
        .map(
          (p) => `<tr>
            <td>${p.paymentId}</td>
            <td>${p.contractId}</td>
            <td>${fmt(p.amount)}</td>
            <td>${makeBadge(p.status)}</td>
            <td>${p.status === "UNVERIFIED" ? `<button class="btn btn-ok" type="button" onclick="confirmPayment(${p.paymentId})">Confirm</button>` : "-"}</td>
          </tr>`,
        )
        .join("")}
    </tbody>
  </table>`;
}

function renderInspect() {
  const box = document.getElementById("inspectList");
  if (!currentUser || currentUser.role !== "accounting") {
    box.innerHTML = "";
    return;
  }

  box.innerHTML = state.shops
    .map(
      (s) => `<div class="card">
        <h4>${s.shopNum}</h4>
        <p class="kv">Type: ${s.type}</p>
        <p class="kv">Area: ${s.area}</p>
        <p class="kv">Status: ${makeBadge(s.status)}</p>
      </div>`,
    )
    .join("");
}

function renderAdminUsers() {
  const box = document.getElementById("adminUserList");
  if (!currentUser || currentUser.role !== "systemadmin") {
    box.innerHTML = "";
    return;
  }

  box.innerHTML = `<table>
    <thead><tr><th>User ID</th><th>Username</th><th>Role</th><th>Permission</th><th>Set Password</th><th>Delete</th></tr></thead>
    <tbody>
      ${state.users
        .map(
          (u) => `<tr>
          <td>${u.userId}</td>
          <td>${u.username}</td>
          <td>${u.role}</td>
          <td>
            <input id="perm-${u.userId}" type="text" value="${state.permissions[u.userId] || "NONE"}" />
            <button class="btn btn-ghost" type="button" onclick="setPermission('${u.userId}')">Save</button>
          </td>
          <td>
            <input id="pwd-${u.username}" type="text" placeholder="new password" />
            <button class="btn btn-ghost" type="button" onclick="setPassword('${u.username}')">Update</button>
          </td>
          <td>
            <button class="btn btn-danger" type="button" onclick="deleteUser('${u.username}')">Delete</button>
          </td>
        </tr>`,
        )
        .join("")}
    </tbody>
  </table>`;
}

function renderAdminShops() {
  const box = document.getElementById("adminShopList");
  if (!currentUser || currentUser.role !== "systemadmin") {
    box.innerHTML = "";
    return;
  }

  box.innerHTML = `<div>${renderShopStatusTable(true)}</div>`;
}

function renderShopStatusTable(includeDelete = false) {
  return `<table>
    <thead><tr><th>ID</th><th>Shop</th><th>Type</th><th>Area</th><th>Status</th><th>Update</th>${includeDelete ? "<th>Delete</th>" : ""}</tr></thead>
    <tbody>
      ${state.shops
        .map(
          (s) => `<tr>
            <td>${s.shopId}</td>
            <td>
              <input id="num-${s.shopId}" type="text" value="${s.shopNum}" />
            </td>
            <td>
              <select id="type-${s.shopId}">
                <option ${s.type === "FNB" ? "selected" : ""}>FNB</option>
                <option ${s.type === "POPUP" ? "selected" : ""}>POPUP</option>
                <option ${s.type === "SOLID" ? "selected" : ""}>SOLID</option>
              </select>
            </td>
            <td>
              <input id="area-${s.shopId}" type="number" min="1" step="0.1" value="${s.area}" />
            </td>
            <td>
              <select id="status-${s.shopId}">
                <option ${s.status === "OPEN" ? "selected" : ""}>OPEN</option>
                <option ${s.status === "CLOSED" ? "selected" : ""}>CLOSED</option>
                <option ${s.status === "UNDER_REPAIR" ? "selected" : ""}>UNDER_REPAIR</option>
              </select>
            </td>
            <td><button class="btn btn-ghost" type="button" onclick="updateShop(${s.shopId})">Save</button></td>
            ${includeDelete ? `<td><button class="btn btn-danger" type="button" onclick="deleteShop(${s.shopId})">Delete</button></td>` : ""}
          </tr>`,
        )
        .join("")}
    </tbody>
  </table>`;
}

function renderLog() {
  const box = document.getElementById("auditLog");
  box.innerHTML = state.logs.length
    ? state.logs.map((l) => `<div>${l}</div>`).join("")
    : "<div>No logs yet.</div>";
}

async function login(username, password) {
  try {
    const result = await api("POST", "/auth/login", { username, password });
    currentUser = result.user;

    await refreshState();

    document.getElementById("welcomeText").textContent =
      `Welcome, ${currentUser.username}`;
    document.getElementById("roleText").textContent = getUserRoleLabel(
      currentUser.role,
    );
    activePanel = (rolePanels[currentUser.role] || [])[0] || null;
    buildNav();
    if (activePanel) {
      showPanel(activePanel);
    }
    showView("appView");
    setAuthMessage("");
    setMessage("");
  } catch (e) {
    setAuthMessage(e.message, true);
  }
}

async function registerTenant(username, password) {
  try {
    await api("POST", "/auth/register", { username, password });
    setAuthMessage("Tenant registered. You can login now.");
  } catch (e) {
    setAuthMessage(e.message, true);
  }
}

async function requestLease(shopId, baseRent) {
  if (!Number.isFinite(baseRent) || baseRent <= 0) {
    setMessage("Please enter a valid base rent.", true);
    return;
  }

  try {
    const result = await api("POST", "/tenant/request-lease", {
      username: currentUser.username,
      shopId,
      baseRent,
    });
    await refreshState();
    setMessage(result.message || "Lease request submitted.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function setActiveContract(contractId) {
  try {
    const result = await api("POST", "/tenant/active-contract", {
      username: currentUser.username,
      contractId,
    });
    await refreshState();
    setMessage(result.message || "Active contract updated.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function approveContract(contractId) {
  try {
    const result = await api("POST", "/manager/approve-contract", {
      contractId,
    });
    await refreshState();
    setMessage(result.message || "Contract approved.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function uploadSales(month, sales) {
  if (!month || !Number.isFinite(sales) || sales < 0) {
    setMessage("Please enter valid month and sales.", true);
    return;
  }

  try {
    const result = await api("POST", "/tenant/upload-sales", {
      username: currentUser.username,
      month,
      sales,
    });
    await refreshState();
    setMessage(result.message || "Payment generated.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function confirmPayment(paymentId) {
  try {
    const result = await api("POST", "/accounting/confirm-payment", {
      paymentId,
    });
    await refreshState();
    setMessage(result.message || "Payment confirmed.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function updateShop(shopId) {
  const body = {
    shopNum: document.getElementById(`num-${shopId}`).value.trim(),
    type: document.getElementById(`type-${shopId}`).value,
    area: Number(document.getElementById(`area-${shopId}`).value),
    status: document.getElementById(`status-${shopId}`).value,
  };

  if (!body.shopNum || !Number.isFinite(body.area) || body.area <= 0) {
    setMessage("Invalid shop fields.", true);
    return;
  }

  try {
    const result = await api("PUT", `/shops/${shopId}`, body);
    await refreshState();
    setMessage(result.message || "Shop updated.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function createShop(shopNum, area, type) {
  if (!shopNum || !Number.isFinite(area) || area <= 0) {
    setMessage("Invalid shop data.", true);
    return;
  }

  try {
    const result = await api("POST", "/shops", { shopNum, area, type });
    await refreshState();
    setMessage(result.message || "Shop created.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function deleteShop(shopId) {
  try {
    const result = await api("DELETE", `/shops/${shopId}`);
    await refreshState();
    setMessage(result.message || "Shop deleted.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function createUser(role, username, password) {
  if (!role || !username || !password) {
    setMessage("Role, username, and password are required.", true);
    return;
  }

  try {
    const result = await api("POST", "/admin/users", {
      role,
      username,
      password,
    });
    await refreshState();
    setMessage(result.message || "User created.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function setPassword(username) {
  const password = document.getElementById(`pwd-${username}`).value.trim();
  if (!password) {
    setMessage("New password cannot be empty.", true);
    return;
  }

  try {
    const result = await api(
      "PUT",
      `/admin/users/${encodeURIComponent(username)}/password`,
      { password },
    );
    await refreshState();
    setMessage(result.message || "Password updated.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function deleteUser(username) {
  try {
    const result = await api(
      "DELETE",
      `/admin/users/${encodeURIComponent(username)}`,
    );
    await refreshState();
    setMessage(result.message || "User deleted.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function setPermission(userId) {
  const permission =
    document.getElementById(`perm-${userId}`).value.trim() || "NONE";

  try {
    const result = await api(
      "PUT",
      `/admin/permissions/${encodeURIComponent(userId)}`,
      { permission },
    );
    await refreshState();
    setMessage(result.message || "Permission updated.");
  } catch (e) {
    setMessage(e.message, true);
  }
}

async function resetData() {
  try {
    await api("POST", "/reset", {});
    currentUser = null;
    state = emptyState();
    showView("loginView");
    await refreshState();
    setAuthMessage("Demo data reset complete.");
    setMessage("");
  } catch (e) {
    setAuthMessage(e.message, true);
  }
}

function logout() {
  currentUser = null;
  showView("loginView");
  setMessage("");
}

function attachEvents() {
  document.getElementById("tabLogin").addEventListener("click", () => {
    document.getElementById("tabLogin").classList.add("active");
    document.getElementById("tabRegister").classList.remove("active");
    document.getElementById("loginForm").classList.add("active");
    document.getElementById("registerForm").classList.remove("active");
    setAuthMessage("");
  });

  document.getElementById("tabRegister").addEventListener("click", () => {
    document.getElementById("tabRegister").classList.add("active");
    document.getElementById("tabLogin").classList.remove("active");
    document.getElementById("registerForm").classList.add("active");
    document.getElementById("loginForm").classList.remove("active");
    setAuthMessage("");
  });

  document.getElementById("loginForm").addEventListener("submit", (e) => {
    e.preventDefault();
    const username = document.getElementById("loginUsername").value.trim();
    const password = document.getElementById("loginPassword").value;
    login(username, password);
  });

  document.getElementById("registerForm").addEventListener("submit", (e) => {
    e.preventDefault();
    const username = document.getElementById("regUsername").value.trim();
    const password = document.getElementById("regPassword").value;
    registerTenant(username, password);
  });

  document.getElementById("salesForm").addEventListener("submit", (e) => {
    e.preventDefault();
    if (!currentUser || currentUser.role !== "tenant") {
      setMessage("Tenant login is required.", true);
      return;
    }
    const month = document.getElementById("salesMonth").value;
    const sales = Number(document.getElementById("salesAmount").value);
    uploadSales(month, sales);
  });

  document.getElementById("createUserForm").addEventListener("submit", (e) => {
    e.preventDefault();
    createUser(
      document.getElementById("newUserRole").value,
      document.getElementById("newUsername").value.trim(),
      document.getElementById("newUserPassword").value,
    );
    e.target.reset();
  });

  document.getElementById("shopForm").addEventListener("submit", (e) => {
    e.preventDefault();
    createShop(
      document.getElementById("shopNum").value.trim(),
      Number(document.getElementById("shopArea").value),
      document.getElementById("shopType").value,
    );
    e.target.reset();
    document.getElementById("shopType").value = "FNB";
  });

  document.getElementById("btnLogout").addEventListener("click", logout);
  document.getElementById("btnResetData").addEventListener("click", resetData);
}

async function bootstrap() {
  attachEvents();
  try {
    await refreshState();
    setAuthMessage("Connected to Java API. Login to continue.");
  } catch (e) {
    setAuthMessage(e.message, true);
  }
}

bootstrap();
