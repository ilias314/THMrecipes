// dark-mode.js

document.addEventListener("DOMContentLoaded", () => {
  // On page load, read localStorage
  const isDarkMode = localStorage.getItem('darkModeEnabled') === 'true';
  if (isDarkMode) {
    document.body.classList.add('dark-mode');
  }
  updateDarkModeIcon(isDarkMode);
});

/**
 * Toggles dark mode on/off, updates localStorage, and changes the icon.
 */
function toggleDarkMode() {
  document.body.classList.toggle('dark-mode');
  const isDark = document.body.classList.contains('dark-mode');
  localStorage.setItem('darkModeEnabled', isDark ? 'true' : 'false');
  updateDarkModeIcon(isDark);
}

/**
 * Switches between fa-moon and fa-sun on the #darkModeIcon element.
 */
function updateDarkModeIcon(isDark) {
  const icon = document.getElementById('darkModeIcon');
  if (!icon) return;
  if (isDark) {
    icon.classList.remove('fa-moon');
    icon.classList.add('fa-sun');
  } else {
    icon.classList.remove('fa-sun');
    icon.classList.add('fa-moon');
  }
}
