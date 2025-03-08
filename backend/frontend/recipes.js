const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  loadRecipes();
});

// Load recipes and render them
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
    console.log("Fetched Recipes:", recipes);
    displayRecipes(recipes);
  } catch (error) {
    console.error("Error fetching recipes:", error);
    alert(error.message);
  }
}

// Render recipes on the page
function displayRecipes(recipes) {
  const recipesList = document.getElementById("recipesList");
  if (!recipesList) {
    console.error("Recipes list element not found!");
    return;
  }
  recipesList.innerHTML = '';

  // Get the logged in userId from localStorage (convert to number)
  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);

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

      // Build extra buttons only if the recipe belongs to the logged-in user
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
            <img src="${imageUrl}" class="card-img-top recipe-image" alt="${recipe.title}"
                 onerror="this.onerror=null;this.src='default-image.jpg';">
            <div class="card-body">
              <h5 class="card-title">${recipe.title}</h5>
              <p class="card-text">${recipe.description || ''}</p>
              <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary mb-2">View Recipe</a>
              ${extraButtons}
            </div>
          </div>
        </div>`;
      row.innerHTML += recipeCard;
    }
    recipesList.appendChild(row);
  }
}

// Function to edit a recipe (using simple prompt dialogs)
async function editRecipe(recipeId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  // First fetch the current recipe details
  try {
    const fetchResponse = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (!fetchResponse.ok) {
      const err = await fetchResponse.json();
      throw new Error(err.message || "Failed to fetch recipe details.");
    }
    const recipe = await fetchResponse.json();

    // Use prompt dialogs to get new values (pre-filled with existing values)
    const newTitle = prompt("Enter new title:", recipe.title);
    if (newTitle === null) return; // User cancelled
    const newDescription = prompt("Enter new description:", recipe.description);
    if (newDescription === null) return;
    const newIngredients = prompt("Enter new ingredients (comma-separated):", Array.isArray(recipe.ingredients) ? recipe.ingredients.join(", ") : "");
    if (newIngredients === null) return;
    const newInstructions = prompt("Enter new instructions (comma-separated):", Array.isArray(recipe.instructions) ? recipe.instructions.join(", ") : "");
    if (newInstructions === null) return;
    // For simplicity, we keep the image unchanged

    const updateData = {
      title: newTitle,
      description: newDescription,
      ingredients: newIngredients,
      instructions: newInstructions,
      image_url: recipe.image_url || "/images/default.png"
    };

    // Send the update request via PUT
    const updateResponse = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "PUT",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(updateData)
    });

    if (updateResponse.ok) {
      alert("Recipe updated successfully!");
      loadRecipes();
    } else {
      const errData = await updateResponse.json();
      throw new Error(errData.message || "Failed to update recipe.");
    }
  } catch (error) {
    console.error("Error updating recipe:", error);
    alert(error.message);
  }
}

// Function to delete a recipe
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.ok) {
      alert("Recipe deleted successfully!");
      loadRecipes();
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete recipe.");
    }
  } catch (error) {
    console.error("Error deleting recipe:", error);
    alert(error.message);
  }
}
