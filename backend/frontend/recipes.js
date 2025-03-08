// recipes.js
const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  loadRecipes();
});

async function loadRecipes() {
  const token = localStorage.getItem("accessToken");

  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
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

    console.log("Response status:", response.status);
    const recipes = await response.json();

    // Log image URLs for debugging
    console.log("Recipe image URLs:", recipes.map(r => r.image_url));
    console.log("Fetched Recipes:", recipes);
    displayRecipes(recipes);
  } catch (error) {
    console.error("Error fetching recipes:", error);
    alert(error.message);
  }
}

function displayRecipes(recipes) {
  const recipesList = document.getElementById("recipesList");
  if (!recipesList) {
    console.error("Recipes list element not found!");
    return;
  }

  recipesList.innerHTML = '';

  for (let i = 0; i < recipes.length; i += 3) {
    const row = document.createElement("div");
    row.className = "row mb-4";

    for (let j = i; j < i + 3 && j < recipes.length; j++) {
      const recipe = recipes[j];

      let imageUrl;
      if (recipe.image_url) {
        imageUrl = recipe.image_url.startsWith('http') || recipe.image_url.startsWith('/')
          ? recipe.image_url
          : `/images/${recipe.image_url}`;
      } else {
        imageUrl = "default-image.jpg";
      }

      const recipeCard = `
        <div class="col-md-4">
          <div class="card shadow-sm">
            <img src="${imageUrl}" class="card-img-top recipe-image" alt="${recipe.title}"
                 onerror="this.onerror=null;this.src='default-image.jpg';">
            <div class="card-body">
              <h5 class="card-title">${recipe.title}</h5>
              <p class="card-text">${recipe.description || ''}</p>
              <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary">View Recipe</a>
            </div>
          </div>
        </div>`;
      row.innerHTML += recipeCard;
    }
    recipesList.appendChild(row);
  }
}
