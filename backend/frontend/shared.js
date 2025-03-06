// shared.js
document.addEventListener('DOMContentLoaded', () => {
  const logoutLink = document.querySelector('a[href="logout"]');
  if (logoutLink) {
    logoutLink.addEventListener('click', (e) => {
      e.preventDefault();
      localStorage.removeItem('token');
      localStorage.removeItem('userId');
      window.location.href = 'login.html';
    });
  }
});
