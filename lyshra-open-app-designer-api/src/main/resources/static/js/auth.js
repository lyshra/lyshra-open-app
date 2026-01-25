/**
 * Authentication utilities for Lyshra OpenApp Designer
 */

const AUTH_TOKEN_KEY = 'lyshra_auth_token';
const USER_DATA_KEY = 'lyshra_user_data';

function getToken() {
    return localStorage.getItem(AUTH_TOKEN_KEY);
}

function setToken(token) {
    localStorage.setItem(AUTH_TOKEN_KEY, token);
}

function clearToken() {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(USER_DATA_KEY);
}

function getUserData() {
    const data = localStorage.getItem(USER_DATA_KEY);
    return data ? JSON.parse(data) : null;
}

function setUserData(data) {
    localStorage.setItem(USER_DATA_KEY, JSON.stringify(data));
}

function isAuthenticated() {
    return !!getToken();
}

async function login(username, password) {
    try {
        const response = await fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const error = await response.json();
            showAlert(error.message || 'Login failed', 'danger');
            return false;
        }

        const data = await response.json();
        setToken(data.token);
        setUserData({
            username: data.username,
            email: data.email,
            roles: data.roles
        });

        window.location.href = '/';
        return true;
    } catch (error) {
        console.error('Login error:', error);
        showAlert('An error occurred during login', 'danger');
        return false;
    }
}

async function register(userData) {
    try {
        const response = await fetch('/api/v1/auth/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(userData)
        });

        if (!response.ok) {
            const error = await response.json();
            showAlert(error.message || 'Registration failed', 'danger');
            return false;
        }

        const data = await response.json();
        setToken(data.token);
        setUserData({
            username: data.username,
            email: data.email,
            roles: data.roles
        });

        window.location.href = '/';
        return true;
    } catch (error) {
        console.error('Registration error:', error);
        showAlert('An error occurred during registration', 'danger');
        return false;
    }
}

function logout() {
    clearToken();
    window.location.href = '/login';
}

function showAlert(message, type = 'info') {
    const container = document.getElementById('alertContainer');
    if (!container) return;

    const alert = document.createElement('div');
    alert.className = `alert alert-${type} alert-dismissible fade show`;
    alert.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;
    container.appendChild(alert);

    setTimeout(() => {
        alert.remove();
    }, 5000);
}

function updateUserDisplay() {
    const userData = getUserData();
    const usernameEl = document.getElementById('username');
    if (usernameEl && userData) {
        usernameEl.textContent = userData.username;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    updateUserDisplay();

    const publicPages = ['/login', '/register'];
    const currentPath = window.location.pathname;

    if (!isAuthenticated() && !publicPages.includes(currentPath)) {
        window.location.href = '/login';
    }
});
