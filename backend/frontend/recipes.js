const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  loadRecipes();
});

async function loadRecipes() {
  const token = localStorage.getItem("token");

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

    if (response.ok) {
      const recipes = await response.json();
      displayRecipes(recipes);
    } else {
      throw new Error("Failed to fetch recipes.");
    }
  } catch (error) {
    console.error("❌ Error fetching recipes:", error);
    alert(error.message);
  }
}

function displayRecipes(recipes) {
  const recipesList = document.getElementById("recipesList");
  recipesList.innerHTML = "";

  recipes.forEach(recipe => {
    let imageUrl = recipe.imageUrl ? `${API_URL}${recipe.imageUrl}` : "default-image.jpg";
    console.log("🖼️ Final Image URL:", imageUrl); // Debugging line

    const recipeCard = `
      <div class="col-md-4 mb-4">
        <div class="card recipe-card">
          <img src="${imageUrl}" class="card-img-top" alt="${recipe.title}">
          <div class="card-body">
            <h5 class="card-title">${recipe.title}</h5>
            <p class="card-text">${recipe.description}</p>
            <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary">View Recipe</a>
          </div>
        </div>
      </div>
    `;
    recipesList.innerHTML += recipeCard;
  });
}

