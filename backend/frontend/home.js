const API_URL = "http://localhost:8080";
const token = localStorage.getItem("token");
const userId = localStorage.getItem("userId");

// 🔄 Benutzerstatus checken
document.addEventListener("DOMContentLoaded", () => {
    if (token) {
        document.getElementById("profileBtn").classList.remove("d-none");
      document.getElementById("logoutBtn").addEventListener("click", () => {
        localStorage.removeItem("token");
        localStorage.removeItem("userId");
        window.location.href = "index.html";
      });

      document.getElementById("loginBtn").classList.add("d-none");
        document.getElementById("registerBtn").classList.add("d-none");
    }
    loadRecipes();
});

// 🔴 Logout-Funktion
document.getElementById("logoutBtn").addEventListener("click", () => {
    localStorage.removeItem("token");
    localStorage.removeItem("userId");
    fetch(`${API_URL}/logout`, { method: "POST", headers: { "Authorization": `Bearer ${token}` } });
    window.location.href = "login.html";
});

// 🟢 Rezepte laden
async function loadRecipes() {
  const token = localStorage.getItem("token");
  try {
    const response = await fetch("http://localhost:8080/recipes", {
      headers: { "Authorization": `Bearer ${token}` }
    });
    const data = await response.json();

    const recipesContainer = document.getElementById("recipesContainer");
    recipesContainer.innerHTML = "";

    data.forEach(recipe => {
      recipesContainer.innerHTML += `
                <div class="col-md-4">
                    <div class="card">
                        <div class="card-body">
                            <h5 class="card-title">${recipe.title}</h5>
                            <p class="card-text">${recipe.description}</p>
                        </div>
                    </div>
                </div>`;
    });
  } catch (error) {
    console.error("Fehler beim Laden der Rezepte:", error);
  }
}

