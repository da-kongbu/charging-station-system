document.addEventListener('DOMContentLoaded', () => {
    renderHeader();
    setupMobileMenu();
});

function getRootPath() {
    // 简单判断当前是否在 pages 目录下
    return window.location.pathname.includes('/pages/') ? '..' : '.';
}

function renderHeader() {
    const user = Auth.getUser();
    const isAuth = Auth.isAuthenticated();
    const root = getRootPath();

    const headerHtml = `
        <nav class="container">
            <a href="${root}/index.html" class="logo">
                <span style="font-size: 24px;">⚡</span> 绿能充电
            </a>
            <div class="nav-links">
                <a href="${root}/index.html">充电站</a>
                ${isAuth ? `
                    <a href="${root}/pages/reservations.html">我的预约</a>
                    <a href="${root}/pages/orders.html">我的订单</a>
                ` : ''}
            </div>
            <div class="auth-buttons">
                ${isAuth ? `
                    <div style="display: flex; align-items: center; gap: 10px;">
                        <span>${user.username || '用户'}</span>
                        <button onclick="Auth.logout()" class="btn btn-outline" style="padding: 4px 12px; font-size: 0.85rem;">退出</button>
                    </div>
                ` : `
                    <a href="${root}/pages/login.html" class="btn btn-outline">登录</a>
                    <a href="${root}/pages/register.html" class="btn btn-primary">注册</a>
                `}
            </div>
        </nav>
    `;

    const header = document.querySelector('header');
    if (header) {
        header.innerHTML = headerHtml;
    }
}

function setupMobileMenu() {
    // 简单的移动端菜单逻辑
}

// 通用工具函数
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.style.position = 'fixed';
    toast.style.top = '20px';
    toast.style.right = '20px';
    toast.style.padding = '12px 24px';
    toast.style.backgroundColor = type === 'success' ? '#00b894' : '#ff7675';
    toast.style.color = 'white';
    toast.style.borderRadius = '8px';
    toast.style.boxShadow = '0 4px 6px rgba(0,0,0,0.1)';
    toast.style.zIndex = '1000';
    toast.textContent = message;

    document.body.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 3000);
}
