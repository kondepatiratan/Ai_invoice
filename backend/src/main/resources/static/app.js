/**
 * InvoxAI - Application Logic & Controllers
 * Dual AI Integration: Google Gemini 2.5 Flash + ChatGPT (OpenAI) Fallback
 */

// Application State
const state = {
  token: localStorage.getItem('invox_token') || null,
  user: JSON.parse(localStorage.getItem('invox_user') || 'null'),
  clients: [],
  invoices: [],
  stats: null,
  activeStatusFilter: '',
  searchQuery: '',
  currentPreviewInvoice: null,
  aiLastGenerated: null,
  charts: {
    revenue: null,
    status: null
  }
};

// DOM Content Loaded Initializer
document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  checkAuthAndInitialize();
});

// -------------------------------------------------------------
// Initialization & Authentication
// -------------------------------------------------------------
function checkAuthAndInitialize() {
  if (state.token && state.user) {
    showApp();
    loadDashboard();
    checkAiHealth();
  } else {
    showAuth();
  }
}

function showAuth() {
  document.getElementById('authScreen').classList.remove('d-none');
  document.getElementById('mainApp').classList.add('d-none');
}

function showApp() {
  document.getElementById('authScreen').classList.add('d-none');
  document.getElementById('mainApp').classList.remove('d-none');

  // Update User Header Info
  const user = state.user || {};
  const initials = (user.name || 'User').split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  document.getElementById('userAvatar').textContent = initials;
  document.getElementById('userNameLabel').textContent = user.name || 'User';
  document.getElementById('userMenuName').textContent = user.name || 'User';
  document.getElementById('userMenuEmail').textContent = user.email || '';
  document.getElementById('userMenuBiz').textContent = user.businessName || 'Business Profile';
}

function setSession(authResponse) {
  state.token = authResponse.token;
  state.user = {
    id: authResponse.id,
    name: authResponse.name,
    email: authResponse.email,
    businessName: authResponse.businessName,
    state: authResponse.state || 'Karnataka'
  };
  localStorage.setItem('invox_token', state.token);
  localStorage.setItem('invox_user', JSON.stringify(state.user));
  showApp();
  loadDashboard();
  checkAiHealth();
}

function logout() {
  localStorage.removeItem('invox_token');
  localStorage.removeItem('invox_user');
  state.token = null;
  state.user = null;
  showAuth();
  showToast('You have been signed out.');
}

// -------------------------------------------------------------
// API Helper
// -------------------------------------------------------------
async function api(endpoint, options = {}) {
  const url = endpoint.startsWith('http') ? endpoint : `/api${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (state.token) {
    headers['Authorization'] = `Bearer ${state.token}`;
  }

  try {
    const res = await fetch(url, { ...options, headers });

    if (res.status === 401) {
      logout();
      throw new Error('Session expired. Please sign in again.');
    }

    if (!res.ok) {
      let errorMsg = `Server error (${res.status})`;
      try {
        const errorData = await res.json();
        errorMsg = errorData.message || errorData.error || errorMsg;
      } catch (e) {}
      throw new Error(errorMsg);
    }

    if (res.status === 204) return null;
    return await res.json();
  } catch (err) {
    console.error(`API Error on ${url}:`, err);
    throw err;
  }
}

// -------------------------------------------------------------
// Toast & UI Notifications
// -------------------------------------------------------------
function showToast(message, isError = false) {
  const toastEl = document.getElementById('liveToast');
  const toastMsg = document.getElementById('toastMessage');
  
  if (isError) {
    toastEl.classList.remove('bg-dark', 'bg-success');
    toastEl.classList.add('bg-danger');
    toastMsg.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i> ${message}`;
  } else {
    toastEl.classList.remove('bg-danger');
    toastEl.classList.add('bg-dark');
    toastMsg.innerHTML = `<i class="fa-solid fa-circle-check text-success"></i> ${message}`;
  }

  const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 4000 });
  toast.show();
}

// -------------------------------------------------------------
// Navigation & Views
// -------------------------------------------------------------
function switchView(viewName) {
  document.querySelectorAll('.app-view').forEach(view => view.classList.add('d-none'));
  document.querySelectorAll('#navLinks .nav-link, .nav-mobile').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.view === viewName);
  });

  // 'ai-studio' -> 'AiStudio' so it matches the section id 'viewAiStudio'
  const viewId = viewName.split('-').map(capitalize).join('');
  const targetView = document.getElementById(`view${viewId}`);
  if (targetView) {
    targetView.classList.remove('d-none');
  }

  if (viewName === 'dashboard') loadDashboard();
  else if (viewName === 'invoices') loadInvoices();
  else if (viewName === 'clients') loadClients();
}

// -------------------------------------------------------------
// View 1: Dashboard Logic
// -------------------------------------------------------------
async function loadDashboard() {
  try {
    const stats = await api('/dashboard/stats');
    state.stats = stats;

    document.getElementById('statPaidRevenue').textContent = formatCurrency(stats.totalRevenue);
    document.getElementById('statPaidCount').textContent = `${stats.paidCount} Paid`;

    document.getElementById('statPendingRevenue').textContent = formatCurrency(stats.pendingRevenue);
    document.getElementById('statSentCount').textContent = `${stats.sentCount} Sent`;

    document.getElementById('statOverdueRevenue').textContent = formatCurrency(stats.overdueRevenue);
    document.getElementById('statOverdueCount').textContent = `${stats.overdueCount} Overdue`;

    document.getElementById('statTotalInvoices').textContent = stats.totalInvoices;
    document.getElementById('statDraftCount').textContent = `${stats.draftCount} Drafts`;
    document.getElementById('statClientCount').textContent = `across ${stats.totalClients} clients`;

    renderRecentInvoices(stats.recentInvoices || []);
    renderDashboardCharts(stats);
  } catch (err) {
    showToast(err.message, true);
  }
}

function renderRecentInvoices(invoices) {
  const tbody = document.getElementById('recentInvoicesBody');
  if (!invoices || invoices.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-muted">No recent invoices found.</td></tr>`;
    return;
  }

  tbody.innerHTML = invoices.map(inv => `
    <tr>
      <td class="ps-4 fw-semibold text-primary cursor-pointer" onclick="viewInvoicePreview(${inv.id})">
        ${inv.invoiceNumber}
      </td>
      <td>
        <div class="fw-semibold text-dark">${inv.client ? inv.client.name : 'Unknown Client'}</div>
        <div class="text-muted small">${inv.client && inv.client.state ? inv.client.state : ''}</div>
      </td>
      <td>${formatDate(inv.issueDate)}</td>
      <td>${formatDate(inv.dueDate)}</td>
      <td class="fw-bold text-dark">${formatCurrency(inv.totalAmount)}</td>
      <td>${renderStatusBadge(inv.status)}</td>
      <td class="text-end pe-4">
        <div class="dropdown d-inline-block">
          <button class="btn btn-light btn-sm rounded-pill border px-2 py-1" type="button" data-bs-toggle="dropdown" data-bs-popper-config='{"strategy":"fixed"}'>
            <i class="fa-solid fa-ellipsis-vertical"></i>
          </button>
          <ul class="dropdown-menu dropdown-menu-end shadow-sm border-0 small">
            <li><a class="dropdown-item" href="javascript:void(0)" onclick="viewInvoicePreview(${inv.id})"><i class="fa-solid fa-eye me-2 text-muted"></i> View / Print</a></li>
            <li><a class="dropdown-item" href="javascript:void(0)" onclick="downloadServerPdf(${inv.id})"><i class="fa-solid fa-download me-2 text-muted"></i> Download Server PDF</a></li>
            ${inv.status === 'SENT' || inv.status === 'OVERDUE' ? `
              <li><a class="dropdown-item text-primary" href="javascript:void(0)" onclick="openReminderModal(${inv.id})"><i class="fa-solid fa-wand-magic-sparkles me-2"></i> AI Payment Reminder</a></li>
              <li><a class="dropdown-item text-success" href="javascript:void(0)" onclick="updateInvoiceStatus(${inv.id}, 'PAID')"><i class="fa-solid fa-check me-2"></i> Mark as Paid</a></li>
            ` : ''}
            ${inv.status === 'DRAFT' ? `
              <li><a class="dropdown-item text-primary" href="javascript:void(0)" onclick="updateInvoiceStatus(${inv.id}, 'SENT')"><i class="fa-solid fa-paper-plane me-2"></i> Mark as Sent</a></li>
              <li><a class="dropdown-item" href="javascript:void(0)" onclick="openEditInvoiceModal(${inv.id})"><i class="fa-solid fa-pen-to-square me-2 text-muted"></i> Edit Draft</a></li>
            ` : ''}
          </ul>
        </div>
      </td>
    </tr>
  `).join('');
}

function renderDashboardCharts(stats) {
  // 1. Revenue Trends Chart
  const ctxRevenue = document.getElementById('revenueChart');
  if (ctxRevenue) {
    if (state.charts.revenue) state.charts.revenue.destroy();

    const trends = stats.monthlyTrends || [];
    // Show only real data: an empty chart is better than made-up numbers.
    const labels = trends.map(t => t.month);
    const dataValues = trends.map(t => t.revenue);

    state.charts.revenue = new Chart(ctxRevenue, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Billed (₹)',
          data: dataValues,
          backgroundColor: '#4f46e5',
          borderRadius: 8,
          barThickness: 36
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false }
        },
        scales: {
          y: {
            beginAtZero: true,
            grid: { color: '#f1f5f9' },
            ticks: {
              callback: val => '₹' + (val >= 1000 ? (val / 1000) + 'k' : val)
            }
          },
          x: {
            grid: { display: false }
          }
        }
      }
    });
  }

  // 2. Status Donut Chart
  const ctxStatus = document.getElementById('statusDonutChart');
  if (ctxStatus) {
    if (state.charts.status) state.charts.status.destroy();

    const counts = [stats.paidCount || 0, stats.sentCount || 0, stats.overdueCount || 0, stats.draftCount || 0];
    const hasInvoices = counts.some(c => c > 0);

    // A new account has no invoices: show one grey "No invoices yet" ring
    // instead of fake equal segments that look like real data.
    state.charts.status = new Chart(ctxStatus, {
      type: 'doughnut',
      data: {
        labels: hasInvoices ? ['Paid', 'Sent', 'Overdue', 'Draft'] : ['No invoices yet'],
        datasets: [{
          data: hasInvoices ? counts : [1],
          backgroundColor: hasInvoices ? ['#10b981', '#3b82f6', '#ef4444', '#94a3b8'] : ['#e2e8f0'],
          borderWidth: 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '72%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: { boxWidth: 12, font: { size: 11 } }
          },
          tooltip: { enabled: hasInvoices }
        }
      }
    });
  }
}

// -------------------------------------------------------------
// View 2: Invoices Manager Logic
// -------------------------------------------------------------
async function loadInvoices() {
  try {
    const statusQuery = state.activeStatusFilter ? `?status=${state.activeStatusFilter}` : '';
    const invoices = await api(`/invoices${statusQuery}`);
    state.invoices = invoices;
    renderAllInvoices();
  } catch (err) {
    showToast(err.message, true);
  }
}

function renderAllInvoices() {
  const tbody = document.getElementById('allInvoicesBody');
  let filtered = state.invoices || [];

  if (state.searchQuery) {
    const q = state.searchQuery.toLowerCase();
    filtered = filtered.filter(inv => 
      inv.invoiceNumber.toLowerCase().includes(q) ||
      (inv.client && inv.client.name.toLowerCase().includes(q))
    );
  }

  if (filtered.length === 0) {
    tbody.innerHTML = `<tr><td colspan="10" class="text-center py-5 text-muted">No invoices found matching criteria.</td></tr>`;
    return;
  }

  tbody.innerHTML = filtered.map(inv => {
    const isIntraState = inv.cgst > 0 || inv.sgst > 0;
    const taxTypeLabel = isIntraState ? 'Intra-State (CGST+SGST)' : 'Inter-State (IGST)';
    const gstTotal = (Number(inv.cgst || 0) + Number(inv.sgst || 0) + Number(inv.igst || 0));

    return `
      <tr>
        <td class="ps-4 fw-semibold text-primary cursor-pointer" onclick="viewInvoicePreview(${inv.id})">
          ${inv.invoiceNumber}
        </td>
        <td>
          <div class="fw-semibold text-dark">${inv.client ? inv.client.name : 'Unknown Client'}</div>
          <div class="text-muted small">${inv.client && inv.client.email ? inv.client.email : ''}</div>
        </td>
        <td>${formatDate(inv.issueDate)}</td>
        <td>${formatDate(inv.dueDate)}</td>
        <td><span class="badge bg-light text-secondary border small">${taxTypeLabel}</span></td>
        <td>${formatCurrency(inv.subtotal)}</td>
        <td class="text-secondary">${formatCurrency(gstTotal)}</td>
        <td class="fw-bold text-dark">${formatCurrency(inv.totalAmount)}</td>
        <td>${renderStatusBadge(inv.status)}</td>
        <td class="text-end pe-4">
          <div class="btn-group">
            <button class="btn btn-light btn-sm border" onclick="viewInvoicePreview(${inv.id})" title="Preview & Print">
              <i class="fa-solid fa-eye"></i>
            </button>
            <button class="btn btn-light btn-sm border" onclick="downloadServerPdf(${inv.id})" title="Download PDF">
              <i class="fa-solid fa-download"></i>
            </button>
            <div class="btn-group">
              <button class="btn btn-light btn-sm border dropdown-toggle" type="button" data-bs-toggle="dropdown" data-bs-popper-config='{"strategy":"fixed"}'></button>
              <ul class="dropdown-menu dropdown-menu-end shadow-sm border-0 small">
                ${inv.status === 'DRAFT' ? `
                  <li><a class="dropdown-item text-primary" href="javascript:void(0)" onclick="updateInvoiceStatus(${inv.id}, 'SENT')"><i class="fa-solid fa-paper-plane me-2"></i> Send Invoice</a></li>
                  <li><a class="dropdown-item" href="javascript:void(0)" onclick="openEditInvoiceModal(${inv.id})"><i class="fa-solid fa-pen-to-square me-2 text-muted"></i> Edit Draft</a></li>
                  <li><a class="dropdown-item text-danger" href="javascript:void(0)" onclick="deleteInvoice(${inv.id})"><i class="fa-solid fa-trash me-2"></i> Delete</a></li>
                ` : ''}
                ${inv.status === 'SENT' || inv.status === 'OVERDUE' ? `
                  <li><a class="dropdown-item text-primary" href="javascript:void(0)" onclick="openReminderModal(${inv.id})"><i class="fa-solid fa-wand-magic-sparkles me-2"></i> Generate AI Reminder</a></li>
                  <li><a class="dropdown-item text-success" href="javascript:void(0)" onclick="updateInvoiceStatus(${inv.id}, 'PAID')"><i class="fa-solid fa-check me-2"></i> Mark as Paid</a></li>
                ` : ''}
                ${inv.status === 'PAID' ? `
                  <li><span class="dropdown-item-text text-muted small"><i class="fa-solid fa-lock me-2"></i> Paid invoices are locked</span></li>
                ` : ''}
              </ul>
            </div>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

async function updateInvoiceStatus(invoiceId, newStatus) {
  try {
    await api(`/invoices/${invoiceId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: newStatus })
    });
    showToast(`Invoice status updated to ${newStatus}`);
    loadDashboard();
    loadInvoices();
  } catch (err) {
    showToast(err.message, true);
  }
}

async function deleteInvoice(invoiceId) {
  if (!confirm('Are you sure you want to delete this draft invoice?')) return;
  try {
    await api(`/invoices/${invoiceId}`, { method: 'DELETE' });
    showToast('Invoice deleted successfully.');
    loadInvoices();
    loadDashboard();
  } catch (err) {
    showToast(err.message, true);
  }
}

// -------------------------------------------------------------
// View 3: AI Studio Logic (Star Feature)
// -------------------------------------------------------------
async function executeAiPrompt() {
  const prompt = document.getElementById('aiPromptTextarea').value.trim();
  if (!prompt) {
    showToast('Please type a prompt describing your invoice scenario.', true);
    return;
  }

  const simulateFallback = document.getElementById('aiSimulateFallback').checked;

  document.getElementById('aiEmptyState').classList.add('d-none');
  document.getElementById('aiResultContent').classList.add('d-none');
  document.getElementById('aiLoadingIndicator').classList.remove('d-none');
  document.getElementById('btnExecuteAiPrompt').disabled = true;

  try {
    const startTime = Date.now();
    const result = await api('/ai/generate-invoice', {
      method: 'POST',
      body: JSON.stringify({
        prompt: prompt,
        simulateFallback: simulateFallback
      })
    });
    const elapsedSec = ((Date.now() - startTime) / 1000).toFixed(1);

    state.aiLastGenerated = result;
    displayAiGeneratedInvoice(result, elapsedSec);
    showToast(`Invoice structured with ${result.providerUsed}!`);
  } catch (err) {
    showToast(err.message, true);
  } finally {
    document.getElementById('aiLoadingIndicator').classList.add('d-none');
    document.getElementById('btnExecuteAiPrompt').disabled = false;
  }
}

function displayAiGeneratedInvoice(data, elapsedSec) {
  document.getElementById('aiResultContent').classList.remove('d-none');

  // Provider Badge
  const badge = document.getElementById('aiProviderBadge');
  badge.textContent = `${data.providerUsed} (${elapsedSec}s)`;
  if (data.providerUsed.includes('Gemini')) {
    badge.className = 'badge bg-success-subtle text-success rounded-pill px-3 py-1';
  } else if (data.providerUsed.includes('ChatGPT')) {
    badge.className = 'badge bg-primary-subtle text-primary rounded-pill px-3 py-1';
  } else {
    badge.className = 'badge bg-secondary-subtle text-secondary rounded-pill px-3 py-1';
  }

  document.getElementById('aiResClient').textContent = data.clientName || 'Acme Technologies';
  document.getElementById('aiResState').textContent = data.clientState || 'Karnataka';
  document.getElementById('aiResIssueDate').textContent = data.issueDate || 'Today';
  document.getElementById('aiResDueDate').textContent = data.dueDate || 'In 15 days';
  document.getElementById('aiResNotes').textContent = data.notes || 'Standard Net 15 terms.';

  const tbody = document.getElementById('aiResItemsBody');
  tbody.innerHTML = (data.items || []).map(item => `
    <tr>
      <td>${item.description}</td>
      <td class="text-end">${item.quantity}</td>
      <td class="text-end">₹${item.rate}</td>
      <td class="text-end">${item.gstRate}%</td>
    </tr>
  `).join('');
}

async function applyAiGeneratedToInvoice(autoSave = false) {
  if (!state.aiLastGenerated) return;
  const gen = state.aiLastGenerated;

  // Check if client exists or create one
  await loadClients();
  let client = state.clients.find(c => c.name.toLowerCase() === gen.clientName.toLowerCase());

  if (!client) {
    try {
      client = await api('/clients', {
        method: 'POST',
        body: JSON.stringify({
          name: gen.clientName,
          email: gen.clientEmail || `billing@${gen.clientName.toLowerCase().replace(/[^a-z0-9]/g, '')}.com`,
          phone: gen.clientPhone || '+91 98765 43210',
          state: gen.clientState || 'Karnataka',
          gstin: gen.clientGstin || '29AAAAA0000A1Z5',
          address: gen.clientAddress || `${gen.clientState || 'Karnataka'}, India`
        })
      });
      await loadClients();
    } catch (e) {
      console.warn('Could not auto-create client:', e);
      client = state.clients[0];
    }
  }

  // Populate Invoice Modal
  openCreateInvoiceModal();
  if (client) {
    document.getElementById('invClientId').value = client.id;
    updateTaxNotice();
  }
  if (gen.issueDate) document.getElementById('invIssueDate').value = gen.issueDate;
  if (gen.dueDate) document.getElementById('invDueDate').value = gen.dueDate;
  if (gen.notes) document.getElementById('invNotes').value = gen.notes;

  // Line items
  const tbody = document.getElementById('invItemsBody');
  tbody.innerHTML = '';
  (gen.items || []).forEach(item => {
    addItemRow(item.description, item.quantity, item.rate, item.gstRate);
  });
  recalculateInvoiceTotals();

  if (autoSave) {
    document.getElementById('btnSaveInvoice').click();
  }
}

// -------------------------------------------------------------
// View 4: Clients Manager Logic
// -------------------------------------------------------------
async function loadClients() {
  try {
    const clients = await api('/clients');
    state.clients = clients || [];
    renderClientsTable();
    populateClientDropdown();
  } catch (err) {
    showToast(err.message, true);
  }
}

function renderClientsTable() {
  const tbody = document.getElementById('clientsTableBody');
  if (!state.clients || state.clients.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-5 text-muted">No clients added yet.</td></tr>`;
    return;
  }

  tbody.innerHTML = state.clients.map(c => `
    <tr>
      <td class="ps-4 fw-semibold text-dark">${c.name}</td>
      <td>${c.email || '—'}</td>
      <td>${c.phone || '—'}</td>
      <td><span class="badge bg-light text-secondary border">${c.state || 'Karnataka'}</span></td>
      <td class="font-monospace small">${c.gstin || '—'}</td>
      <td class="small text-muted text-truncate" style="max-width: 200px;">${c.address || '—'}</td>
      <td class="text-end pe-4">
        <button class="btn btn-outline-danger btn-sm rounded-pill px-3" onclick="deleteClient(${c.id})">
          <i class="fa-solid fa-trash me-1"></i> Delete
        </button>
      </td>
    </tr>
  `).join('');
}

function populateClientDropdown() {
  const select = document.getElementById('invClientId');
  if (!select) return;
  const currentVal = select.value;

  select.innerHTML = `<option value="">-- Choose Client --</option>` + 
    state.clients.map(c => `
      <option value="${c.id}" data-state="${c.state || ''}">${c.name} (${c.state || 'N/A'})</option>
    `).join('');

  if (currentVal) select.value = currentVal;
}

async function saveClient(e) {
  e.preventDefault();
  const name = document.getElementById('clientName').value.trim();
  const email = document.getElementById('clientEmail').value.trim();
  const phone = document.getElementById('clientPhone').value.trim();
  const stateVal = document.getElementById('clientState').value;
  const gstin = document.getElementById('clientGstin').value.trim().toUpperCase();
  const address = document.getElementById('clientAddress').value.trim();

  try {
    await api('/clients', {
      method: 'POST',
      body: JSON.stringify({ name, email, phone, state: stateVal, gstin, address })
    });
    showToast(`Client '${name}' added successfully!`);
    bootstrap.Modal.getInstance(document.getElementById('clientModal')).hide();
    document.getElementById('clientForm').reset();
    await loadClients();
  } catch (err) {
    showToast(err.message, true);
  }
}

async function deleteClient(clientId) {
  if (!confirm('Are you sure you want to delete this client?')) return;
  try {
    await api(`/clients/${clientId}`, { method: 'DELETE' });
    showToast('Client deleted.');
    loadClients();
  } catch (err) {
    showToast(err.message, true);
  }
}

// -------------------------------------------------------------
// Invoice Form Modal (Create / Edit) & Real-time GST Math
// -------------------------------------------------------------
function openCreateInvoiceModal() {
  document.getElementById('invFormId').value = '';
  document.getElementById('invoiceModalTitle').innerHTML = `<i class="fa-solid fa-file-invoice me-2"></i> Create New Invoice`;
  document.getElementById('invoiceForm').reset();

  const today = new Date().toISOString().split('T')[0];
  const due = new Date(Date.now() + 15 * 86400000).toISOString().split('T')[0];
  document.getElementById('invIssueDate').value = today;
  document.getElementById('invDueDate').value = due;

  const tbody = document.getElementById('invItemsBody');
  tbody.innerHTML = '';
  addItemRow('Enterprise Software Architecture Consulting', 40, 2500, 18);

  populateClientDropdown();
  updateTaxNotice();
  recalculateInvoiceTotals();

  new bootstrap.Modal(document.getElementById('invoiceModal')).show();
}

async function openEditInvoiceModal(invoiceId) {
  try {
    const inv = await api(`/invoices/${invoiceId}`);
    document.getElementById('invFormId').value = inv.id;
    document.getElementById('invoiceModalTitle').innerHTML = `<i class="fa-solid fa-pen-to-square me-2"></i> Edit Invoice ${inv.invoiceNumber}`;

    populateClientDropdown();
    document.getElementById('invClientId').value = inv.client ? inv.client.id : '';
    document.getElementById('invIssueDate').value = inv.issueDate;
    document.getElementById('invDueDate').value = inv.dueDate;
    document.getElementById('invNotes').value = inv.notes || '';

    const tbody = document.getElementById('invItemsBody');
    tbody.innerHTML = '';
    (inv.items || []).forEach(item => {
      addItemRow(item.description, item.quantity, item.rate, item.gstRate);
    });

    updateTaxNotice();
    recalculateInvoiceTotals();

    new bootstrap.Modal(document.getElementById('invoiceModal')).show();
  } catch (err) {
    showToast(err.message, true);
  }
}

function addItemRow(desc = '', qty = 1, rate = 1000, gstRate = 18) {
  const tbody = document.getElementById('invItemsBody');
  const rowId = 'row_' + Math.random().toString(36).substring(2, 9);

  const tr = document.createElement('tr');
  tr.id = rowId;
  tr.innerHTML = `
    <td>
      <div class="input-group">
        <input type="text" class="form-control item-desc" placeholder="e.g. Spring Boot REST API Development" value="${escapeHtml(desc)}" required>
        <button type="button" class="btn btn-outline-primary btn-sm px-2 btn-ai-enhance" title="AI Enhance: Auto-generate description & SAC code" onclick="enhanceRowDescription('${rowId}')">
          <i class="fa-solid fa-wand-magic-sparkles text-amber"></i> AI
        </button>
      </div>
    </td>
    <td>
      <input type="number" class="form-control text-end item-qty" value="${qty}" min="0.01" step="any" oninput="recalculateInvoiceTotals()" required>
    </td>
    <td>
      <input type="number" class="form-control text-end item-rate" value="${rate}" min="0" step="any" oninput="recalculateInvoiceTotals()" required>
    </td>
    <td>
      <select class="form-select text-end item-gst" onchange="recalculateInvoiceTotals()">
        <option value="0" ${Number(gstRate) === 0 ? 'selected' : ''}>0%</option>
        <option value="5" ${Number(gstRate) === 5 ? 'selected' : ''}>5%</option>
        <option value="12" ${Number(gstRate) === 12 ? 'selected' : ''}>12%</option>
        <option value="18" ${Number(gstRate) === 18 ? 'selected' : ''}>18%</option>
        <option value="28" ${Number(gstRate) === 28 ? 'selected' : ''}>28%</option>
      </select>
    </td>
    <td class="text-end fw-semibold item-amount">₹0.00</td>
    <td class="text-center">
      <button type="button" class="btn btn-link text-danger p-0" onclick="removeItemRow('${rowId}')" title="Remove">
        <i class="fa-solid fa-trash"></i>
      </button>
    </td>
  `;
  tbody.appendChild(tr);
  recalculateInvoiceTotals();
}

function removeItemRow(rowId) {
  const tbody = document.getElementById('invItemsBody');
  if (tbody.children.length <= 1) {
    showToast('Invoice must have at least one line item.', true);
    return;
  }
  document.getElementById(rowId).remove();
  recalculateInvoiceTotals();
}

async function enhanceRowDescription(rowId) {
  const row = document.getElementById(rowId);
  const descInput = row.querySelector('.item-desc');
  const rateInput = row.querySelector('.item-rate');
  const gstSelect = row.querySelector('.item-gst');
  const btn = row.querySelector('.btn-ai-enhance');

  const title = descInput.value.trim();
  if (!title) {
    showToast('Type a brief title (e.g. "Cloud migration" or "UI Design") before enhancing.', true);
    return;
  }

  btn.innerHTML = `<span class="spinner-border spinner-border-sm"></span>`;
  btn.disabled = true;

  try {
    const res = await api('/ai/enhance-description', {
      method: 'POST',
      body: JSON.stringify({ title })
    });
    descInput.value = `${res.title} - ${res.enhancedDescription} (SAC: ${res.hsnSacCode})`;
    if (res.suggestedRate) rateInput.value = res.suggestedRate;
    if (res.recommendedGstRate) gstSelect.value = res.recommendedGstRate;

    recalculateInvoiceTotals();
    showToast(`Line item enhanced via ${res.providerUsed}!`);
  } catch (err) {
    showToast(err.message, true);
  } finally {
    btn.innerHTML = `<i class="fa-solid fa-wand-magic-sparkles text-amber"></i> AI`;
    btn.disabled = false;
  }
}

function updateTaxNotice() {
  const select = document.getElementById('invClientId');
  const banner = document.getElementById('taxBannerText');
  const badge = document.getElementById('taxBannerBadge');

  if (!select || !select.value) {
    banner.textContent = 'Select a client to calculate intra-state vs inter-state GST';
    badge.textContent = 'Auto-GST';
    return;
  }

  const selectedOpt = select.options[select.selectedIndex];
  const clientState = (selectedOpt.getAttribute('data-state') || '').trim().toLowerCase();
  const userState = ((state.user && state.user.state) || 'Karnataka').trim().toLowerCase();

  const isIntraState = clientState === userState;

  if (isIntraState) {
    banner.innerHTML = `<strong>Intra-State Supply (${capitalize(userState)} &rarr; ${capitalize(clientState)}):</strong> Tax will be split equally into <strong>CGST (50%)</strong> and <strong>SGST (50%)</strong>.`;
    badge.className = 'badge bg-success-subtle text-success border border-success-subtle';
    badge.textContent = 'CGST + SGST (Intra-State)';
  } else {
    banner.innerHTML = `<strong>Inter-State Supply (${capitalize(userState)} &rarr; ${capitalize(clientState || 'Other')}):</strong> Full tax will be applied as <strong>IGST (Integrated GST)</strong>.`;
    badge.className = 'badge bg-primary-subtle text-primary border border-primary-subtle';
    badge.textContent = 'IGST (Inter-State)';
  }

  recalculateInvoiceTotals();
}

function recalculateInvoiceTotals() {
  const rows = document.querySelectorAll('#invItemsBody tr');
  let subtotal = 0;
  let totalTax = 0;

  const select = document.getElementById('invClientId');
  let isIntraState = true;
  if (select && select.value) {
    const selectedOpt = select.options[select.selectedIndex];
    const clientState = (selectedOpt.getAttribute('data-state') || '').trim().toLowerCase();
    const userState = ((state.user && state.user.state) || 'Karnataka').trim().toLowerCase();
    isIntraState = clientState === userState;
  }

  rows.forEach(row => {
    const qty = parseFloat(row.querySelector('.item-qty').value) || 0;
    const rate = parseFloat(row.querySelector('.item-rate').value) || 0;
    const gstPct = parseFloat(row.querySelector('.item-gst').value) || 0;

    const lineAmount = qty * rate;
    const lineTax = (lineAmount * gstPct) / 100;

    row.querySelector('.item-amount').textContent = '₹' + lineAmount.toFixed(2);
    subtotal += lineAmount;
    totalTax += lineTax;
  });

  const grandTotal = subtotal + totalTax;

  document.getElementById('calcSubtotal').textContent = '₹' + subtotal.toFixed(2);

  const cgstRow = document.getElementById('calcCgstRow');
  const sgstRow = document.getElementById('calcSgstRow');
  const igstRow = document.getElementById('calcIgstRow');

  if (isIntraState) {
    cgstRow.classList.remove('d-none');
    sgstRow.classList.remove('d-none');
    igstRow.classList.add('d-none');
    const halfTax = (totalTax / 2).toFixed(2);
    document.getElementById('calcCgst').textContent = '₹' + halfTax;
    document.getElementById('calcSgst').textContent = '₹' + halfTax;
  } else {
    cgstRow.classList.add('d-none');
    sgstRow.classList.add('d-none');
    igstRow.classList.remove('d-none');
    document.getElementById('calcIgst').textContent = '₹' + totalTax.toFixed(2);
  }

  document.getElementById('calcTotal').textContent = '₹' + grandTotal.toFixed(2);
}

async function saveInvoice() {
  const clientId = document.getElementById('invClientId').value;
  const issueDate = document.getElementById('invIssueDate').value;
  const dueDate = document.getElementById('invDueDate').value;
  const notes = document.getElementById('invNotes').value.trim();
  const formId = document.getElementById('invFormId').value;

  if (!clientId) {
    showToast('Please select a client.', true);
    return;
  }

  const rows = document.querySelectorAll('#invItemsBody tr');
  const items = [];
  for (const row of rows) {
    const desc = row.querySelector('.item-desc').value.trim();
    const qty = parseFloat(row.querySelector('.item-qty').value);
    const rate = parseFloat(row.querySelector('.item-rate').value);
    const gstRate = parseFloat(row.querySelector('.item-gst').value);

    if (!desc) {
      showToast('All items must have a description.', true);
      return;
    }
    items.push({ description: desc, quantity: qty, rate: rate, gstRate: gstRate });
  }

  const payload = {
    clientId: parseInt(clientId),
    issueDate,
    dueDate,
    notes,
    items
  };

  try {
    if (formId) {
      await api(`/invoices/${formId}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
      showToast('Invoice updated successfully!');
    } else {
      await api('/invoices', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      showToast('Invoice created successfully!');
    }

    bootstrap.Modal.getInstance(document.getElementById('invoiceModal')).hide();
    loadDashboard();
    loadInvoices();
  } catch (err) {
    showToast(err.message, true);
  }
}

// -------------------------------------------------------------
// View / Print / PDF Generation Logic
// -------------------------------------------------------------
async function viewInvoicePreview(invoiceId) {
  try {
    const inv = await api(`/invoices/${invoiceId}`);
    state.currentPreviewInvoice = inv;

    document.getElementById('previewStatusBadge').outerHTML = renderStatusBadge(inv.status, 'previewStatusBadge');

    const bizName = (state.user && state.user.businessName) || (state.user && state.user.name) || 'Nivuna Cloud Innovations';
    document.getElementById('prevBizName').textContent = bizName;
    document.getElementById('prevBizGstin').textContent = 'GSTIN: ' + ((state.user && state.user.gstin) || '29ABCDE1234F1Z5');
    document.getElementById('prevBizState').textContent = 'State: ' + ((state.user && state.user.state) || 'Karnataka');
    document.getElementById('prevBizEmail').textContent = 'Email: ' + (state.user && state.user.email);

    document.getElementById('prevInvoiceNumber').textContent = inv.invoiceNumber;
    document.getElementById('prevIssueDate').textContent = 'Issue Date: ' + formatDate(inv.issueDate);
    document.getElementById('prevDueDate').textContent = 'Due Date: ' + formatDate(inv.dueDate);

    const client = inv.client || {};
    document.getElementById('prevClientName').textContent = client.name || 'Valued Client';
    document.getElementById('prevClientEmail').textContent = client.email || '';
    document.getElementById('prevClientPhone').textContent = client.phone || '';
    document.getElementById('prevClientGstin').textContent = 'GSTIN: ' + (client.gstin || 'N/A');
    document.getElementById('prevClientAddress').textContent = client.address || '';

    const isIntraState = Number(inv.cgst || 0) > 0 || Number(inv.sgst || 0) > 0;
    document.getElementById('prevPlaceOfSupply').textContent = 'Place of Supply: ' + (client.state || 'Karnataka');
    document.getElementById('prevTaxType').textContent = isIntraState ? 'Intra-State (CGST + SGST)' : 'Inter-State (IGST)';

    const tbody = document.getElementById('prevItemsBody');
    tbody.innerHTML = (inv.items || []).map((item, idx) => `
      <tr>
        <td class="text-center">${idx + 1}</td>
        <td>${escapeHtml(item.description)}</td>
        <td class="text-end">${item.quantity}</td>
        <td class="text-end">₹${parseFloat(item.rate).toFixed(2)}</td>
        <td class="text-end">${item.gstRate}%</td>
        <td class="text-end fw-semibold">₹${parseFloat(item.amount).toFixed(2)}</td>
      </tr>
    `).join('');

    document.getElementById('prevNotes').textContent = inv.notes || 'Payment due on receipt. Please mention invoice number in payment reference.';
    document.getElementById('prevSubtotal').textContent = '₹' + parseFloat(inv.subtotal).toFixed(2);

    const cgstRow = document.getElementById('prevCgstRow');
    const sgstRow = document.getElementById('prevSgstRow');
    const igstRow = document.getElementById('prevIgstRow');

    if (isIntraState) {
      cgstRow.classList.remove('d-none');
      sgstRow.classList.remove('d-none');
      igstRow.classList.add('d-none');
      document.getElementById('prevCgst').textContent = '₹' + parseFloat(inv.cgst).toFixed(2);
      document.getElementById('prevSgst').textContent = '₹' + parseFloat(inv.sgst).toFixed(2);
    } else {
      cgstRow.classList.add('d-none');
      sgstRow.classList.add('d-none');
      igstRow.classList.remove('d-none');
      document.getElementById('prevIgst').textContent = '₹' + parseFloat(inv.igst).toFixed(2);
    }

    document.getElementById('prevGrandTotal').textContent = '₹' + parseFloat(inv.totalAmount).toFixed(2);

    new bootstrap.Modal(document.getElementById('previewModal')).show();
  } catch (err) {
    showToast(err.message, true);
  }
}

function downloadServerPdf(invoiceId) {
  const targetId = invoiceId || (state.currentPreviewInvoice && state.currentPreviewInvoice.id);
  if (!targetId) return;

  const url = `/api/invoices/${targetId}/pdf?token=${encodeURIComponent(state.token)}`;
  window.open(url, '_blank');
}

// -------------------------------------------------------------
// AI Payment Reminder Modal Logic
// -------------------------------------------------------------
async function openReminderModal(invoiceId) {
  document.getElementById('reminderInvoiceId').value = invoiceId;
  document.getElementById('reminderSubject').value = 'Generating AI reminder...';
  document.getElementById('reminderBody').value = 'Consulting Gemini / ChatGPT for optimal follow-up copy...';

  new bootstrap.Modal(document.getElementById('reminderModal')).show();
  await refreshReminderCopy();
}

async function refreshReminderCopy() {
  const invoiceId = document.getElementById('reminderInvoiceId').value;
  const tone = document.querySelector('input[name="reminderTone"]:checked').value;

  try {
    const res = await api('/ai/reminder', {
      method: 'POST',
      body: JSON.stringify({
        invoiceId: parseInt(invoiceId),
        tone: tone
      })
    });
    document.getElementById('reminderSubject').value = res.subject;
    document.getElementById('reminderBody').value = res.messageBody;
    document.getElementById('reminderProviderBadge').textContent = `Generated by ${res.providerUsed}`;
  } catch (err) {
    showToast(err.message, true);
  }
}

function copyReminderToClipboard() {
  const subject = document.getElementById('reminderSubject').value;
  const body = document.getElementById('reminderBody').value;
  const fullText = `Subject: ${subject}\n\n${body}`;

  navigator.clipboard.writeText(fullText).then(() => {
    showToast('Reminder copied to clipboard!');
  }).catch(() => {
    showToast('Could not copy to clipboard.', true);
  });
}

// -------------------------------------------------------------
// AI Health Status Check
// -------------------------------------------------------------
async function checkAiHealth() {
  try {
    const status = await api('/ai/health');
    const label = document.getElementById('aiStatusLabel');
    if (status.geminiConfigured) {
      label.textContent = `Gemini (${status.geminiModel})`;
    } else if (status.openaiConfigured) {
      label.textContent = `ChatGPT (${status.openaiModel})`;
    } else {
      label.textContent = `Smart Rules Fallback`;
    }
  } catch (e) {
    console.warn('AI health ping failed:', e);
  }
}

// -------------------------------------------------------------
// DOM Event Listeners
// -------------------------------------------------------------
function setupEventListeners() {
  // Login Form
  document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value;
    try {
      const res = await api('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password })
      });
      setSession(res);
      showToast('Welcome back, ' + res.name + '!');
    } catch (err) {
      showToast(err.message, true);
    }
  });

  // Register Form
  document.getElementById('registerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('regName').value.trim();
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value;
    const businessName = document.getElementById('regBusinessName').value.trim();
    const stateVal = document.getElementById('regState').value;
    const gstin = document.getElementById('regGstin').value.trim().toUpperCase();

    try {
      const res = await api('/auth/register', {
        method: 'POST',
        body: JSON.stringify({ name, email, password, businessName, state: stateVal, gstin })
      });
      setSession(res);
      showToast('Account created successfully!');
    } catch (err) {
      showToast(err.message, true);
    }
  });

  // Sign out
  document.getElementById('btnLogout').addEventListener('click', (e) => {
    e.preventDefault();
    logout();
  });

  // Navigation Links
  document.querySelectorAll('#navLinks .nav-link, .nav-mobile, [data-view]').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const view = btn.dataset.view;
      if (view) {
        e.preventDefault();
        switchView(view);
      }
    });
  });

  // Quick Action Buttons
  document.getElementById('btnTopNewInvoice').addEventListener('click', openCreateInvoiceModal);
  document.getElementById('btnDashNewInvoice').addEventListener('click', openCreateInvoiceModal);
  document.getElementById('btnInvoicesNew').addEventListener('click', openCreateInvoiceModal);

  document.getElementById('btnDashAiQuick').addEventListener('click', () => switchView('ai-studio'));
  document.getElementById('btnInvoicesAiPrompt').addEventListener('click', () => switchView('ai-studio'));

  // Invoices Filters & Search
  document.querySelectorAll('#invoiceStatusFilters .btn-filter').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#invoiceStatusFilters .btn-filter').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.activeStatusFilter = btn.dataset.status;
      loadInvoices();
    });
  });

  document.getElementById('invoiceSearchInput').addEventListener('input', (e) => {
    state.searchQuery = e.target.value.trim();
    renderAllInvoices();
  });

  // AI Studio Listeners
  document.getElementById('btnExecuteAiPrompt').addEventListener('click', executeAiPrompt);
  document.querySelectorAll('.prompt-sample-pill').forEach(pill => {
    pill.addEventListener('click', () => {
      document.getElementById('aiPromptTextarea').value = pill.dataset.prompt;
    });
  });
  document.getElementById('btnAiApplyToInvoice').addEventListener('click', () => applyAiGeneratedToInvoice(true));
  document.getElementById('btnAiEditForm').addEventListener('click', () => applyAiGeneratedToInvoice(false));

  // Client Modal
  document.getElementById('btnNewClient').addEventListener('click', () => {
    document.getElementById('clientForm').reset();
    new bootstrap.Modal(document.getElementById('clientModal')).show();
  });
  document.getElementById('btnQuickAddClient').addEventListener('click', () => {
    document.getElementById('clientForm').reset();
    new bootstrap.Modal(document.getElementById('clientModal')).show();
  });
  document.getElementById('clientForm').addEventListener('submit', saveClient);

  // Invoice Form
  document.getElementById('btnAddItemRow').addEventListener('click', () => addItemRow());
  document.getElementById('invClientId').addEventListener('change', updateTaxNotice);
  document.getElementById('btnSaveInvoice').addEventListener('click', saveInvoice);

  // Print & PDF
  document.getElementById('btnPrintInvoice').addEventListener('click', () => window.print());
  document.getElementById('btnDownloadServerPdf').addEventListener('click', () => downloadServerPdf());

  // Payment Reminder
  document.querySelectorAll('input[name="reminderTone"]').forEach(radio => {
    radio.addEventListener('change', refreshReminderCopy);
  });
  document.getElementById('btnCopyReminder').addEventListener('click', copyReminderToClipboard);

  // AI Settings Modal
  document.getElementById('btnAiSettings').addEventListener('click', () => {
    new bootstrap.Modal(document.getElementById('aiConfigModal')).show();
  });
  document.getElementById('menuAiConfig').addEventListener('click', (e) => {
    e.preventDefault();
    new bootstrap.Modal(document.getElementById('aiConfigModal')).show();
  });
  document.getElementById('btnAiStudioConfig').addEventListener('click', () => {
    new bootstrap.Modal(document.getElementById('aiConfigModal')).show();
  });
}

// -------------------------------------------------------------
// Utility Formatting Helpers
// -------------------------------------------------------------
function formatCurrency(amount) {
  const num = parseFloat(amount || 0);
  return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch (e) {
    return dateStr;
  }
}

function renderStatusBadge(status, idAttr = '') {
  const idStr = idAttr ? `id="${idAttr}"` : '';
  switch (status) {
    case 'PAID':
      return `<span ${idStr} class="badge badge-status-paid rounded-pill px-3 py-1"><i class="fa-solid fa-circle-check me-1"></i> PAID</span>`;
    case 'SENT':
      return `<span ${idStr} class="badge badge-status-sent rounded-pill px-3 py-1"><i class="fa-solid fa-paper-plane me-1"></i> SENT</span>`;
    case 'OVERDUE':
      return `<span ${idStr} class="badge badge-status-overdue rounded-pill px-3 py-1"><i class="fa-solid fa-triangle-exclamation me-1"></i> OVERDUE</span>`;
    case 'DRAFT':
    default:
      return `<span ${idStr} class="badge badge-status-draft rounded-pill px-3 py-1"><i class="fa-solid fa-file-pen me-1"></i> DRAFT</span>`;
  }
}

function capitalize(str) {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1);
}

function escapeHtml(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
