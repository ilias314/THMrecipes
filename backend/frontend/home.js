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
    console.error("❌ Fehler: 'recipe-container' nicht gefunden.");
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

    if (!response.ok) {
      throw new Error(`Fehler beim Laden der Rezepte: ${response.status} ${response.statusText}`);
    }

    const recipes = await response.json();
    const totalRecipes = recipes.length;
    const totalPages = Math.ceil(totalRecipes / itemsPerPage);

    const startIndex = (page - 1) * itemsPerPage;
    const selectedRecipes = recipes.slice(startIndex, startIndex + itemsPerPage);

    recipeContainer.innerHTML = "";

    if (selectedRecipes.length === 0) {
      recipeContainer.innerHTML = "<p class='text-center'>Keine Rezepte vorhanden.</p>";
      return;
    }

    selectedRecipes.forEach(recipe => {
      const imageUrl = recipe.image_url && recipe.image_url.startsWith("/images/")
        ? `${API_URL}${recipe.image_url}`
        : `${API_URL}/images/default.png`;

      const card = `
                <div class="col-md-4 mb-3">
                    <div class="card shadow-sm">
                        <img src="${imageUrl}" class="card-img-top" alt="Rezeptbild">
                        <div class="card-body">
                            <h5 class="card-title">${recipe.title}</h5>
                            <p class="card-text">${recipe.description}</p>
                            <button class="btn btn-primary" onclick="viewRecipe(${recipe.id})">Details</button>
                        </div>
                    </div>
                </div>`;
      recipeContainer.innerHTML += card;
    });

    paginationContainer.innerHTML = `
            <button class="btn btn-outline-primary" id="prevPage" ${page === 1 ? "disabled" : ""}>⬅ Vorherige</button>
            <span class="mx-3">Seite ${page} von ${totalPages}</span>
            <button class="btn btn-outline-primary" id="nextPage" ${page === totalPages ? "disabled" : ""}>Nächste ➡</button>
        `;

    document.getElementById("prevPage")?.addEventListener("click", () => changePage(-1));
    document.getElementById("nextPage")?.addEventListener("click", () => changePage(1));

  } catch (error) {
    console.error("❌ Fehler beim Laden der Rezepte:", error);
    recipeContainer.innerHTML = `<p class='text-danger'>❌ Fehler beim Laden der Rezepte: ${error.message}</p>`;
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
