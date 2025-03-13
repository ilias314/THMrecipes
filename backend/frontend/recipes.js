// recipes.js

const API_URL = "http://localhost:8080";
let currentPage = 1;
const itemsPerPage = 12; // Number of recipes to show per page

// We'll store the recipe ID that we're editing in a global variable
let currentEditedRecipeId = null;

document.addEventListener("DOMContentLoaded", () => {
  loadRecipes(currentPage);
});

async function loadRecipes(page) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    showCustomToast("Please log in first!", "danger");
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
    showCustomToast(error.message, "danger");
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
            <img src="${imageUrl}" class="card-img-top recipe-image" alt="${recipe.title}"
                 onerror="this.onerror=null;this.src='default-image.jpg';" />
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
    stars += i <= rounded
      ? '<i class="fas fa-star" style="color: #FFD700;"></i>'
      : '<i class="far fa-star" style="color: #FFD700;"></i>';
  }
  stars += ` <span class="ms-2">(${average.toFixed(1)})</span>`;
  return stars;
}

/**
 * Renders the pagination controls (Previous, Next) at the bottom.
 */
function renderPaginationControls(totalPages, currentPage) {
  const paginationContainer = document.getElementById("pagination-container");
  if (!paginationContainer) return;

  paginationContainer.innerHTML = `
    <button class="btn-outline-primary me-2" id="prevPage" ${currentPage === 1 ? "disabled" : ""}>
      <i class="fas fa-chevron-left"></i>
    </button>
    <span id="pageInfo" class="minimalist-info">Page ${currentPage} of ${totalPages}</span>
    <button class="btn-outline-primary ms-2" id="nextPage" ${currentPage === totalPages ? "disabled" : ""}>
      <i class="fas fa-chevron-right"></i>
    </button>
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

/**
 * Edit an existing recipe (opens the Bootstrap modal).
 */
async function editRecipe(recipeId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    showCustomToast("Please log in first!", "danger");
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

    // Fill in modal form fields
    document.getElementById("recipeTitleInput").value = recipe.title || "";
    document.getElementById("recipeDescriptionInput").value = recipe.description || "";
    document.getElementById("recipeIngredientsInput").value = Array.isArray(recipe.ingredients)
      ? recipe.ingredients.join(", ")
      : recipe.ingredients || "";
    document.getElementById("recipeInstructionsInput").value = Array.isArray(recipe.instructions)
      ? recipe.instructions.join(", ")
      : recipe.instructions || "";

    // Store recipeId in global variable
    currentEditedRecipeId = recipeId;

    // Show the Bootstrap modal
    const modalEl = document.getElementById("editRecipeModal");
    const modalInstance = new bootstrap.Modal(modalEl);
    modalInstance.show();
  } catch (error) {
    console.error("Error loading recipe for edit:", error);
    showCustomToast(error.message, "danger");
  }
}

/**
 * Called when user clicks "Save Changes" in the Edit Recipe modal.
 */
async function updateRecipeFromModal() {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    showCustomToast("Please log in first!", "danger");
    window.location.href = "login.html";
    return;
  }
  if (!currentEditedRecipeId) {
    showCustomToast("No recipe is currently being edited.", "danger");
    return;
  }

  // Collect updated info
  const newTitle = document.getElementById("recipeTitleInput").value;
  const newDescription = document.getElementById("recipeDescriptionInput").value;
  const newIngredients = document.getElementById("recipeIngredientsInput").value;
  const newInstructions = document.getElementById("recipeInstructionsInput").value;

  const updateData = {
    title: newTitle,
    description: newDescription,
    ingredients: newIngredients,      // Will be processed by backend
    instructions: newInstructions,    // Will be processed by backend
  };

  try {
    const response = await fetch(`${API_URL}/recipes/${currentEditedRecipeId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(updateData),
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to update recipe.");
    }

    // Hide the modal
    const modalEl = document.getElementById("editRecipeModal");
    const modalInstance = bootstrap.Modal.getInstance(modalEl);
    modalInstance.hide();

    showCustomToast("Recipe updated successfully!", "success");
    // Reload the recipe list
    loadRecipes(currentPage);
  } catch (error) {
    console.error("Error updating recipe:", error);
    showCustomToast(error.message, "danger");
  }
}

/**
 * Delete a recipe using a confirmation modal.
 */
function deleteRecipe(recipeId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    showCustomToast("Please log in first!", "danger");
    window.location.href = "login.html";
    return;
  }

  showConfirmationDialog(
    "Are you sure you want to delete this recipe? This action cannot be undone.",
    async () => {
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
        showCustomToast("Recipe deleted successfully!", "success");
        loadRecipes(currentPage);
      } catch (error) {
        console.error("Error deleting recipe:", error);
        showCustomToast(error.message, "danger");
      }
    }
  );
}

/**
 * Add a recipe to wishlist (unchanged).
 */
async function addRecipeToWishlist(recipeId) {
  const token = localStorage.getItem("accessToken");
  const userId = localStorage.getItem("userId");
  if (!token || !userId) {
    showCustomToast("Please log in first!", "danger");
    window.location.href = "login.html";
    return;
  }
  try {
    const response = await fetch(`${API_URL}/wishlist`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ user_id: parseInt(userId, 10), recipe_id: recipeId }),
    });
    if (response.ok) {
      showCustomToast("Added to wishlist!", "success");
    } else if (response.status === 409) {
      showCustomToast("Recipe is already in your wishlist.", "warning");
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add to wishlist");
    }
  } catch (error) {
    console.error("Error adding to wishlist:", error);
    showCustomToast(error.message, "danger");
  }
}

/**
 * Logout helper.
 */
function logout(event) {
  if (event) event.preventDefault();
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}

/**
 * Custom paging approach from older code. If used, keep it or remove if not needed.
 */
function changePage(direction) {
  currentPage += direction;
  loadRecipes(currentPage);
  // Scroll to the top smoothly
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
