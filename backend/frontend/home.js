const API_URL = "http://localhost:8080";
const token = localStorage.getItem("token");
const userId = localStorage.getItem("userId");
let currentPage = 1;
const itemsPerPage = 12;

document.addEventListener("DOMContentLoaded", () => {
  setupNavigation();
  loadRecipes(currentPage);
  document.getElementById("logoutBtn")?.addEventListener("click", logout);
});

function setupNavigation() {
  const loginBtn = document.getElementById("loginBtn");
  const registerBtn = document.getElementById("registerBtn");
  const profileBtn = document.getElementById("profileBtn");
  const recipesBtn = document.getElementById("recipesBtn");
  const logoutBtn = document.getElementById("logoutBtn");

  if (token && userId) {
    loginBtn?.classList.add("d-none");
    registerBtn?.classList.add("d-none");
    profileBtn?.classList.remove("d-none");
    recipesBtn?.classList.remove("d-none");
    logoutBtn?.classList.remove("d-none");
  } else {
    profileBtn?.classList.add("d-none");
    recipesBtn?.classList.add("d-none");
    logoutBtn?.classList.add("d-none");
  }
}

async function loadRecipes(page) {
  const recipeContainer = document.getElementById("recipe-container");
  const paginationContainer = document.getElementById("pagination-container");

  if (!recipeContainer) {
    console.error("Fehler: 'recipe-container' nicht gefunden.");
    return;
  }

  if (!token) {
    console.warn("⚠ Kein Token gefunden! Bitte einloggen.");
    recipeContainer.innerHTML = "<p class='text-danger'>⚠ Du musst eingeloggt sein, um Rezepte zu sehen.</p>";
    return;
  }

  try {
    const response = await fetch(`${API_URL}/recipes`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (!response.ok) throw new Error(`Fehler: ${response.status} ${response.statusText}`);
    const recipes = await response.json();
    // ... (Display logic remains the same)
  } catch (error) {
    console.error("Fehler beim Laden der Rezepte:", error);
    recipeContainer.innerHTML = `<p class='text-danger'> ${error.message}</p>`;
  }
}

function changePage(direction) {
  currentPage += direction;
  loadRecipes(currentPage);
}

function viewRecipe(id) {
  window.location.href = `recipe.html?id=${id}`;
}

function logout() {
  localStorage.removeItem("token");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}
