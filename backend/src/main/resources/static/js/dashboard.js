const API_URL = '/api/health/dashboard';
const REFRESH_INTERVAL = 30000;

function getAuthHeaders() {
    const token = localStorage.getItem('jwt_token');
    const headers = { 'Content-Type': 'application/json' };
    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }
    return headers;
}

function isAuthenticated() {
    return localStorage.getItem('jwt_token') !== null;
}

function redirectToLogin() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user_email');
    localStorage.removeItem('user_role');
    window.location.href = '/login';
}

async function fetchHealthData() {
    if (!isAuthenticated()) {
        redirectToLogin();
        return;
    }

    try {
        const response = await fetch(API_URL, {
            headers: getAuthHeaders()
        });
        
        if (response.status === 401) {
            redirectToLogin();
            return;
        }
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        renderDashboard(data.data);
        hideError();
    } catch (error) {
        console.error('Error fetching health data:', error);
        showError(error.message);
    }
}

function renderDashboard(services) {
    const tableBody = document.getElementById('healthTable');
    tableBody.innerHTML = '';

    if (!services || services.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="5" class="px-6 py-8 text-center text-gray-500">No services found</td></tr>';
        updateStatistics([]);
        return;
    }

    services.forEach(service => {
        const row = createServiceRow(service);
        tableBody.appendChild(row);
    });

    updateStatistics(services);
}

function createServiceRow(service) {
    const row = document.createElement('tr');
    row.className = 'hover:bg-gray-50 transition';

    const statusClass = getStatusClass(service.status);
    const statusBadge = getStatusBadge(service.status);
    const lastLogTime = formatTime(service.lastLogTime);

    row.innerHTML = `
        <td class="px-6 py-4 whitespace-nowrap">
            <div class="text-sm font-medium text-gray-900">${escapeHtml(service.service)}</div>
        </td>
        <td class="px-6 py-4 whitespace-nowrap">
            <span class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium ${statusClass}">
                <span class="w-2 h-2 rounded-full mr-2 ${getStatusIndicatorClass(service.status)}"></span>
                ${statusBadge}
            </span>
        </td>
        <td class="px-6 py-4 whitespace-nowrap">
            <div class="text-sm text-gray-900">${service.errorRate.toFixed(2)}%</div>
        </td>
        <td class="px-6 py-4 whitespace-nowrap">
            <div class="text-sm text-gray-600">${lastLogTime}</div>
        </td>
        <td class="px-6 py-4 whitespace-nowrap text-sm">
            <a href="/api/analytics/error-rate?service=${encodeURIComponent(service.service)}" 
               class="text-blue-600 hover:text-blue-900 mr-4">
                <i class="fas fa-chart-bar"></i> Analytics
            </a>
        </td>
    `;

    return row;
}

function getStatusClass(status) {
    const classes = {
        'GREEN': 'bg-green-100 text-green-800',
        'YELLOW': 'bg-yellow-100 text-yellow-800',
        'RED': 'bg-red-100 text-red-800',
        'UNKNOWN': 'bg-gray-100 text-gray-800'
    };
    return classes[status] || classes['UNKNOWN'];
}

function getStatusIndicatorClass(status) {
    const classes = {
        'GREEN': 'bg-green-600',
        'YELLOW': 'bg-yellow-600',
        'RED': 'bg-red-600',
        'UNKNOWN': 'bg-gray-600'
    };
    return classes[status] || classes['UNKNOWN'];
}

function getStatusBadge(status) {
    const badges = {
        'GREEN': '<i class="fas fa-check mr-1"></i>Healthy',
        'YELLOW': '<i class="fas fa-exclamation mr-1"></i>Degraded',
        'RED': '<i class="fas fa-times mr-1"></i>Unhealthy',
        'UNKNOWN': '<i class="fas fa-question mr-1"></i>Unknown'
    };
    return badges[status] || badges['UNKNOWN'];
}

function updateStatistics(services) {
    const total = services.length;
    const healthy = services.filter(s => s.status === 'GREEN').length;
    const degraded = services.filter(s => s.status === 'YELLOW').length;
    const unhealthy = services.filter(s => s.status === 'RED').length;

    document.getElementById('totalServices').textContent = total;
    document.getElementById('healthyCount').textContent = healthy;
    document.getElementById('degradedCount').textContent = degraded;
    document.getElementById('unhealthyCount').textContent = unhealthy;
}

function formatTime(isoString) {
    if (!isoString) return 'N/A';
    const date = new Date(isoString);
    return date.toLocaleString();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showError(message) {
    const errorAlert = document.getElementById('errorAlert');
    const errorMessage = document.getElementById('errorMessage');
    errorMessage.textContent = message;
    errorAlert.classList.remove('hidden');
}

function hideError() {
    document.getElementById('errorAlert').classList.add('hidden');
}

document.getElementById('refreshBtn')?.addEventListener('click', fetchHealthData);

if (isAuthenticated()) {
    fetchHealthData();
    setInterval(fetchHealthData, REFRESH_INTERVAL);
} else {
    redirectToLogin();
}
