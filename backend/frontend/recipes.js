// recipes.js

const API_URL = "http://localhost:8080";
let currentPage = 1;
const itemsPerPage = 12; // Number of recipes to show per page

document.addEventListener("DOMContentLoaded", () => {
  loadRecipes(currentPage);
});

async function loadRecipes(page) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  try {
    // Fetch all recipes from the backend
    const response = await fetch(`${API_URL}/recipes`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      },
    });

    if (!response.ok) {
      throw new Error(`Error loading recipes: ${response.status} ${response.statusText}`);
    }

    const recipes = await response.json();

    // Calculate pagination
    const totalRecipes = recipes.length;
    const totalPages = Math.ceil(totalRecipes / itemsPerPage);
    const startIndex = (page - 1) * itemsPerPage;
    const selectedRecipes = recipes.slice(startIndex, startIndex + itemsPerPage);

    displayRecipes(selectedRecipes);
    renderPaginationControls(totalPages, page);
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
  recipesList.innerHTML = "";

  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);

  // Group recipes in rows (3 per row)
  for (let i = 0; i < recipes.length; i += 3) {
    const row = document.createElement("div");
    row.className = "row mb-4";

    for (let j = i; j < i + 3 && j < recipes.length; j++) {
      const recipe = recipes[j];
      let imageUrl = recipe.image_url || "default-image.jpg";
      if (!imageUrl.startsWith("http") && !imageUrl.startsWith("/")) {
        imageUrl = `/images/${imageUrl}`;
      }

      // Only display Edit/Delete if the recipe belongs to the logged-in user
      let extraButtons = "";
      if (parseInt(recipe.user_id, 10) === loggedInUserId) {
        extraButtons = `
          <button class="btn btn-warning me-1" onclick="editRecipe(${recipe.id})">
            <i class="fas fa-edit"></i>
          </button>
          <button class="btn btn-danger" onclick="deleteRecipe(${recipe.id})">
            <i class="fas fa-trash"></i>
          </button>
        `;
      }

      const recipeCard = `
        <div class="col-md-4 mb-3">
          <div class="card shadow-sm">
            <img src="${imageUrl}" class="card-img-top recipe-image" alt="${recipe.title}" onerror="this.onerror=null;this.src='default-image.jpg';" />
            <div class="card-body">
              <h5 class="card-title">${recipe.title}</h5>
              <p class="card-text">${recipe.description || ""}</p>
              <!-- Star rating container -->
              <div id="avgRating-${recipe.id}" class="mb-2"></div>
              <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary mb-2">View Recipe</a>
              <button class="btn btn-primary mb-2" onclick="addRecipeToWishlist(${recipe.id})">
                <i class="fas fa-heart"></i>
              </button>
              ${extraButtons}
            </div>
          </div>
        </div>
      `;
      row.innerHTML += recipeCard;
    }
    recipesList.appendChild(row);
  }

  // For each displayed recipe, fetch and display its average rating
  recipes.forEach(recipe => {
    fetchAndDisplayAverageRating(recipe.id);
  });
}

async function fetchAndDisplayAverageRating(recipeId) {
  try {
    const token = localStorage.getItem("accessToken");
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
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
    ratings.forEach(r => (sum += r.rating));
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
    stars += i <= rounded ? '<i class="fas fa-star" style="color: #FFD700;"></i>'
      : '<i class="far fa-star" style="color: #FFD700;"></i>';
  }
  stars += ` <span class="ms-2">(${average.toFixed(1)})</span>`;
  return stars;
}

function renderPaginationControls(totalPages, currentPage) {
  const paginationContainer = document.getElementById("pagination-container");
  if (!paginationContainer) return;

  paginationContainer.innerHTML = `
    <button class="btn btn-outline-primary" id="prevPage" ${currentPage === 1 ? "disabled" : ""}>⬅ Previous</button>
    <span class="mx-3">Page ${currentPage} of ${totalPages}</span>
    <button class="btn btn-outline-primary" id="nextPage" ${currentPage === totalPages ? "disabled" : ""}>Next ➡</button>
  `;

  document.getElementById("prevPage")?.addEventListener("click", () => {
    if (currentPage > 1) {
      currentPage--;
      loadRecipes(currentPage);
    }
  });
  document.getElementById("nextPage")?.addEventListener("click", () => {
    if (currentPage < totalPages) {
      currentPage++;
      loadRecipes(currentPage);
    }
  });
}

async function editRecipe(recipeId) {
  // (Edit functionality remains unchanged)
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
        "Content-Type": "application/json"
      },
    });
    if (!fetchResponse.ok) {
      const err = await fetchResponse.json();
      throw new Error(err.message || "Failed to fetch recipe details.");
    }
    const recipe = await fetchResponse.json();
    // Open edit modal (if using modal editing)
    // For now, we're using prompts (or implement your modal logic)
    const newTitle = prompt("Enter new title:", recipe.title);
    if (newTitle === null) return;
    const newDescription = prompt("Enter new description:", recipe.description);
    if (newDescription === null) return;
    const newIngredients = prompt("Enter new ingredients (comma-separated):", Array.isArray(recipe.ingredients) ? recipe.ingredients.join(", ") : "");
    if (newIngredients === null) return;
    const newInstructions = prompt("Enter new instructions (comma-separated):", Array.isArray(recipe.instructions) ? recipe.instructions.join(", ") : "");
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
        "Content-Type": "application/json"
      },
      body: JSON.stringify(updateData),
    });
    if (!updateResponse.ok) {
      const errData = await updateResponse.json();
      throw new Error(errData.message || "Failed to update recipe.");
    }
    alert("Recipe updated successfully!");
    loadRecipes(currentPage);
  } catch (error) {
    console.error("Error updating recipe:", error);
    alert(error.message);
  }
}

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
        "Content-Type": "application/json"
      },
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete recipe.");
    }
    alert("Recipe deleted successfully!");
    loadRecipes(currentPage);
  } catch (error) {
    console.error("Error deleting recipe:", error);
    alert(error.message);
  }
}

async function addRecipeToWishlist(recipeId) {
  const token = localStorage.getItem("accessToken");
  const userId = localStorage.getItem("userId");
  if (!token || !userId) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  try {
    const response = await fetch(`${API_URL}/wishlist`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify({ user_id: parseInt(userId, 10), recipe_id: recipeId })
    });
    if (response.ok) {
      alert("Added to wishlist!");
    } else if (response.status === 409) {
      alert("Recipe is already in your wishlist.");
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add to wishlist");
    }
  } catch (error) {
    console.error("Error adding to wishlist:", error);
    alert(error.message);
  }
}

function logout(event) {
  if (event) event.preventDefault();
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}
function changePage(direction) {
  currentPage += direction;
  loadRecipes(currentPage);
  // Scroll to the top smoothly after changing page
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderPaginationControls(totalPages, currentPage) {
  const paginationContainer = document.getElementById("pagination-container");
  if (!paginationContainer) return;

  paginationContainer.innerHTML = `
    <button class="btn custom-pagination-btn me-3" id="prevPage" ${currentPage === 1 ? "disabled" : ""}>
      <i class="fas fa-chevron-left"></i> Previous
    </button>
    <span id="pageInfo" class="custom-pagination-info">Page ${currentPage} of ${totalPages}</span>
    <button class="btn custom-pagination-btn ms-3" id="nextPage" ${currentPage === totalPages ? "disabled" : ""}>
      Next <i class="fas fa-chevron-right"></i>
    </button>
  `;

  document.getElementById("prevPage")?.addEventListener("click", () => changePage(-1));
  document.getElementById("nextPage")?.addEventListener("click", () => changePage(1));
}
