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
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });

    const recipes = await response.json();
    displayRecipes(recipes);
  } catch (error) {
    console.error("Error fetching recipes:", error);
    alert(error.message);
  }
}

async function displayRecipes(recipes) {
  const recipesList = document.getElementById("recipesList");
  if (!recipesList) {
    console.error("Recipes list element not found!");
    return;
  }
  recipesList.innerHTML = "";

  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);

  // We group 3 recipes per row
  for (let i = 0; i < recipes.length; i += 3) {
    const row = document.createElement("div");
    row.className = "row mb-4";

    for (let j = i; j < i + 3 && j < recipes.length; j++) {
      const recipe = recipes[j];
      let imageUrl = recipe.image_url || "default-image.jpg";
      if (!imageUrl.startsWith("http") && !imageUrl.startsWith("/")) {
        imageUrl = `/images/${imageUrl}`;
      }

      let extraButtons = "";
      if (parseInt(recipe.user_id, 10) === loggedInUserId) {
        extraButtons = `
          <button class="btn btn-secondary me-1" onclick="editRecipe(${recipe.id})">Edit</button>
          <button class="btn btn-danger" onclick="deleteRecipe(${recipe.id})">Delete</button>
        `;
      }

      const recipeCard = `
        <div class="col-md-4 mb-3">
          <div class="card shadow-sm">
            <img
              src="${imageUrl}"
              class="card-img-top recipe-image"
              alt="${recipe.title}"
              onerror="this.onerror=null;this.src='default-image.jpg';"
            />
            <div class="card-body">
              <h5 class="card-title">${recipe.title}</h5>
              <p class="card-text">${recipe.description || ""}</p>
              <!-- Star rating container -->
              <div id="avgRating-${recipe.id}" class="mb-2"></div>
              <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary mb-2">
                View Recipe
              </a>
              ${extraButtons}
            </div>
          </div>
        </div>
      `;
      row.innerHTML += recipeCard;
    }
    recipesList.appendChild(row);
  }

  // For each recipe, fetch & display average rating
  for (const recipe of recipes) {
    await fetchAndDisplayAverageRating(recipe.id);
  }
}

async function fetchAndDisplayAverageRating(recipeId) {
  try {
    const token = localStorage.getItem("accessToken");
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) throw new Error("Failed to fetch ratings");

    const ratings = await response.json();
    const ratingContainer = document.getElementById(`avgRating-${recipeId}`);
    if (!ratingContainer) return;

    if (!Array.isArray(ratings) || ratings.length === 0) {
      ratingContainer.textContent = "No ratings yet.";
      return;
    }

    let sum = 0;
    ratings.forEach((r) => (sum += r.rating));
    const avg = sum / ratings.length;
    ratingContainer.innerHTML = getStarsHtml(avg);
  } catch (err) {
    console.error("Error fetching average rating:", err);
  }
}

function getStarsHtml(average) {
  const rounded = Math.round(average);
  let stars = "";
  for (let i = 1; i <= 5; i++) {
    if (i <= rounded) {
      stars += '<i class="fas fa-star" style="color: #FFD700;"></i>';
    } else {
      stars += '<i class="far fa-star" style="color: #FFD700;"></i>';
    }
  }
  stars += ` <span class="ms-2">(${average.toFixed(1)})</span>`;
  return stars;
}

// Edit Recipe (using prompts)
async function editRecipe(recipeId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  try {
    const fetchResponse = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!fetchResponse.ok) {
      const err = await fetchResponse.json();
      throw new Error(err.message || "Failed to fetch recipe details.");
    }
    const recipe = await fetchResponse.json();

    const newTitle = prompt("Enter new title:", recipe.title);
    if (newTitle === null) return;
    const newDescription = prompt("Enter new description:", recipe.description);
    if (newDescription === null) return;
    const newIngredients = prompt(
      "Enter new ingredients (comma-separated):",
      Array.isArray(recipe.ingredients) ? recipe.ingredients.join(", ") : ""
    );
    if (newIngredients === null) return;
    const newInstructions = prompt(
      "Enter new instructions (comma-separated):",
      Array.isArray(recipe.instructions) ? recipe.instructions.join(", ") : ""
    );
    if (newInstructions === null) return;

    const updateData = {
      title: newTitle,
      description: newDescription,
      ingredients: newIngredients,
      instructions: newInstructions,
      image_url: recipe.image_url || "/images/default.png",
    };

    const updateResponse = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(updateData),
    });
    if (!updateResponse.ok) {
      const errData = await updateResponse.json();
      throw new Error(errData.message || "Failed to update recipe.");
    }
    alert("Recipe updated successfully!");
    loadRecipes();
  } catch (error) {
    console.error("Error updating recipe:", error);
    alert(error.message);
  }
}

// Delete Recipe
async function deleteRecipe(recipeId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  if (!confirm("Are you sure you want to delete this recipe? This action cannot be undone.")) {
    return;
  }
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete recipe.");
    }
    alert("Recipe deleted successfully!");
    loadRecipes();
  } catch (error) {
    console.error("Error deleting recipe:", error);
    alert(error.message);
  }
}
