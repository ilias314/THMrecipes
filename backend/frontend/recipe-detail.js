const API_URL = "http://localhost:8080";
let currentRecipe = null;
let currentCommentId = null;
let currentRatingId = null;

document.addEventListener("DOMContentLoaded", () => {
  const urlParams = new URLSearchParams(window.location.search);
  const recipeId = urlParams.get("id");

  if (recipeId) {
    // 1) Fetch recipe details
    fetchRecipeDetails(recipeId);
    // 2) Fetch comments
    fetchComments(recipeId);
    // 3) Fetch ratings
    fetchRatings(recipeId);

    // Setup comment form
    const commentForm = document.getElementById("commentForm");
    if (commentForm) {
      commentForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const content = document.getElementById("comment").value;
        addComment(recipeId, content);
      });
    }

    // Setup rating form
    const ratingForm = document.getElementById("ratingForm");
    if (ratingForm) {
      ratingForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const ratingValue = document.getElementById("rating").value;
        addRating(recipeId, ratingValue);
      });
    }
  } else {
    console.error("❌ Recipe ID not found in URL.");
  }
});

// ---------- Recipe Loading & Editing ----------
async function fetchRecipeDetails(recipeId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to fetch recipe details.");
    }
    const recipe = await response.json();
    currentRecipe = recipe;
    displayRecipeDetails(recipe);
  } catch (error) {
    console.error("❌ Error fetching recipe details:", error);
    alert(error.message);
  }
}

function displayRecipeDetails(recipe) {
  const recipeTitle = document.getElementById("recipeTitle");
  const recipeDescription = document.getElementById("recipeDescription");
  const ingredientsList = document.getElementById("ingredientsList");
  const instructionsList = document.getElementById("instructionsList");
  const recipeActions = document.getElementById("recipeActions");
  const recipeImage = document.getElementById("recipeImage");

  // Basic text
  recipeTitle.textContent = recipe.title;
  recipeDescription.textContent = recipe.description;

  // Image
  let imageUrl = recipe.image_url || "default-image.jpg";
  if (!imageUrl.startsWith("http") && !imageUrl.startsWith("/")) {
    imageUrl = `/images/${imageUrl}`;
  }
  recipeImage.src = imageUrl;

  // Ingredients
  ingredientsList.innerHTML = "";
  if (Array.isArray(recipe.ingredients)) {
    recipe.ingredients.forEach((ingredient) => {
      const li = document.createElement("li");
      li.textContent = ingredient;
      ingredientsList.appendChild(li);
    });
  }

  // Instructions
  instructionsList.innerHTML = "";
  if (Array.isArray(recipe.instructions)) {
    recipe.instructions.forEach((instruction) => {
      const li = document.createElement("li");
      li.textContent = instruction;
      instructionsList.appendChild(li);
    });
  }

  // If owner, show edit/delete
  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);
  if (recipe.user_id === loggedInUserId) {
    recipeActions.innerHTML = `
      <button class="btn btn-secondary me-2" onclick="openEditRecipeModal()">Edit</button>
      <button class="btn btn-danger" onclick="deleteRecipe(${recipe.id})">Delete</button>
    `;
  } else {
    recipeActions.innerHTML = "";
  }
}

function openEditRecipeModal() {
  if (!currentRecipe) {
    alert("Recipe details not loaded yet.");
    return;
  }
  document.getElementById("recipeTitleInput").value = currentRecipe.title;
  document.getElementById("recipeDescriptionInput").value = currentRecipe.description;
  document.getElementById("recipeIngredientsInput").value = Array.isArray(currentRecipe.ingredients)
    ? currentRecipe.ingredients.join(", ")
    : currentRecipe.ingredients;
  document.getElementById("recipeInstructionsInput").value = Array.isArray(currentRecipe.instructions)
    ? currentRecipe.instructions.join("\n")
    : currentRecipe.instructions;

  const modalInstance = new bootstrap.Modal(document.getElementById("editRecipeModal"));
  modalInstance.show();
}

async function updateRecipeFromModal() {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  if (!currentRecipe) return;
  const recipeId = currentRecipe.id;

  const newTitle = document.getElementById("recipeTitleInput").value;
  const newDescription = document.getElementById("recipeDescriptionInput").value;
  const newIngredients = document.getElementById("recipeIngredientsInput").value;
  const newInstructions = document.getElementById("recipeInstructionsInput").value;

  const updateData = {
    title: newTitle,
    description: newDescription,
    ingredients: newIngredients,
    instructions: newInstructions,
    image_url: currentRecipe.image_url || "/images/default.png",
  };

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}`, {
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
    alert("Recipe updated successfully!");
    fetchRecipeDetails(recipeId);
    const modalEl = document.getElementById("editRecipeModal");
    const modalInstance = bootstrap.Modal.getInstance(modalEl);
    modalInstance.hide();
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
  if (!confirm("Are you sure you want to delete this recipe?")) {
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
    window.location.href = "recipes.html";
  } catch (error) {
    console.error("Error deleting recipe:", error);
    alert(error.message);
  }
}

// ---------- Comments ----------
async function addComment(recipeId, content) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/comments`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ content }),
    });
    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add comment.");
    }
    fetchComments(recipeId);
    document.getElementById("comment").value = "";
  } catch (error) {
    console.error("❌ Error adding comment:", error);
    alert(error.message);
  }
}

async function fetchComments(recipeId) {
  const token = localStorage.getItem("accessToken");
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/comments`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch comments.");
    }
    let comments = await response.json();
    if (!Array.isArray(comments)) comments = [];
    displayComments(comments);
  } catch (error) {
    console.error("❌ Error fetching comments:", error);
    alert(error.message);
  }
}

function displayComments(comments) {
  const commentsList = document.getElementById("commentsList");
  commentsList.innerHTML = "";
  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);

  comments.forEach((c) => {
    let commentHtml = `
      <div class="mb-2">
        <strong>${c.username}</strong> <small>${c.createdAt}</small>
        <p>${c.content}</p>`;
    if (parseInt(c.userId, 10) === loggedInUserId) {
      commentHtml += `
        <button class="btn btn-secondary btn-sm me-1" onclick="openEditCommentModal(${c.id}, '${encodeURIComponent(c.content)}')">Edit</button>
        <button class="btn btn-danger btn-sm" onclick="deleteComment(${c.id})">Delete</button>`;
    }
    commentHtml += "</div>";
    commentsList.innerHTML += commentHtml;
  });
}

function openEditCommentModal(commentId, encodedContent) {
  currentCommentId = commentId;
  const content = decodeURIComponent(encodedContent);
  document.getElementById("commentContentInput").value = content;
  const modalInstance = new bootstrap.Modal(document.getElementById("editCommentModal"));
  modalInstance.show();
}

async function updateCommentFromModal() {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  if (!currentCommentId) {
    alert("No comment selected for editing.");
    return;
  }
  const newContent = document.getElementById("commentContentInput").value;
  try {
    const response = await fetch(`${API_URL}/comments/${currentCommentId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ content: newContent }),
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to update comment.");
    }
    alert("Comment updated successfully!");
    const recipeId = new URLSearchParams(window.location.search).get("id");
    fetchComments(recipeId);
    const modalEl = document.getElementById("editCommentModal");
    const modalInstance = bootstrap.Modal.getInstance(modalEl);
    modalInstance.hide();
  } catch (error) {
    console.error("Error updating comment:", error);
    alert(error.message);
  }
}

async function deleteComment(commentId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  if (!confirm("Are you sure you want to delete this comment?")) {
    return;
  }
  try {
    const response = await fetch(`${API_URL}/comments/${commentId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete comment.");
    }
    alert("Comment deleted successfully!");
    const recipeId = new URLSearchParams(window.location.search).get("id");
    fetchComments(recipeId);
  } catch (error) {
    console.error("Error deleting comment:", error);
    alert(error.message);
  }
}

// ---------- Ratings ----------
async function fetchRatings(recipeId) {
  const token = localStorage.getItem("accessToken");
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to fetch ratings.");
    }
    let ratings = await response.json();
    if (!Array.isArray(ratings)) ratings = [];

    displayRatings(ratings);
  } catch (error) {
    console.error("❌ Error fetching ratings:", error);
    alert(error.message);
  }
}

function displayRatings(ratings) {
  const ratingsList = document.getElementById("ratingsList");
  ratingsList.innerHTML = "";

  if (ratings.length === 0) {
    ratingsList.textContent = "No ratings yet.";
    return;
  }
  // Compute average
  let sum = 0;
  ratings.forEach((r) => (sum += r.rating));
  const avg = sum / ratings.length;

  // Show average rating as stars
  ratingsList.innerHTML += `<div class="mb-2">Average rating: ${getStarsHtml(avg)}</div>`;

  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);
  // Show each rating (optional)
  ratings.forEach((rating) => {
    let ratingHtml = `
      <div class="mb-2">
        <strong>${rating.username}</strong> <small>${rating.createdAt}</small>
        <p>Rating: ${rating.rating}</p>`;
    if (parseInt(rating.userId, 10) === loggedInUserId) {
      ratingHtml += `
        <button class="btn btn-secondary btn-sm me-1" onclick="openEditRatingModal(${rating.id}, ${rating.rating})">Edit</button>
        <button class="btn btn-danger btn-sm" onclick="deleteRating(${rating.id})">Delete</button>`;
    }
    ratingHtml += "</div>";
    ratingsList.innerHTML += ratingHtml;
  });
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

async function addRating(recipeId, ratingValue) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ rating: parseInt(ratingValue, 10) }),
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to add rating.");
    }
    fetchRatings(recipeId);
  } catch (error) {
    console.error("❌ Error adding rating:", error);
    alert(error.message);
  }
}

function openEditRatingModal(ratingId, currentValue) {
  currentRatingId = ratingId;
  document.getElementById("ratingValueInput").value = currentValue;
  const modalInstance = new bootstrap.Modal(document.getElementById("editRatingModal"));
  modalInstance.show();
}

async function updateRatingFromModal() {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  if (!currentRatingId) {
    alert("No rating selected for editing.");
    return;
  }
  const newRating = parseInt(document.getElementById("ratingValueInput").value, 10);
  try {
    const response = await fetch(`${API_URL}/ratings/${currentRatingId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ rating: newRating }),
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to update rating.");
    }
    alert("Rating updated successfully!");
    const recipeId = new URLSearchParams(window.location.search).get("id");
    fetchRatings(recipeId);

    const modalEl = document.getElementById("editRatingModal");
    const modalInstance = bootstrap.Modal.getInstance(modalEl);
    modalInstance.hide();
  } catch (error) {
    console.error("Error updating rating:", error);
    alert(error.message);
  }
}

async function deleteRating(ratingId) {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
  if (!confirm("Are you sure you want to delete this rating?")) {
    return;
  }
  try {
    const response = await fetch(`${API_URL}/ratings/${ratingId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (!response.ok) {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete rating.");
    }
    alert("Rating deleted successfully!");
    const recipeId = new URLSearchParams(window.location.search).get("id");
    fetchRatings(recipeId);
  } catch (error) {
    console.error("Error deleting rating:", error);
    alert(error.message);
  }
}
