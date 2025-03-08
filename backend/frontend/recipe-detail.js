// recipe-detail.js
const API_URL = "http://localhost:8080";
let currentRecipe = null;     // Stores the current recipe details
let currentCommentId = null;  // Stores the comment ID being edited
let currentRatingId = null;   // Stores the rating ID being edited

document.addEventListener("DOMContentLoaded", () => {
  const urlParams = new URLSearchParams(window.location.search);
  const recipeId = urlParams.get("id");

  if (recipeId) {
    fetchRecipeDetails(recipeId); // Fetch recipe details and store in currentRecipe
    fetchComments(recipeId);      // Fetch comments
    fetchRatings(recipeId);       // Fetch ratings

    // Comment form submit
    const commentForm = document.getElementById("commentForm");
    if (commentForm) {
      commentForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const content = document.getElementById("comment").value;
        addComment(recipeId, content);
      });
    }

    // Rating form submit
    const ratingForm = document.getElementById("ratingForm");
    if (ratingForm) {
      ratingForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const rating = document.getElementById("rating").value;
        addRating(recipeId, rating);
      });
    }
  } else {
    console.error("❌ Recipe ID not found in URL.");
  }
});

// ---------- Recipe Functions ----------
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (response.ok) {
      const recipe = await response.json();
      currentRecipe = recipe;
      displayRecipeDetails(recipe);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch recipe details.");
    }
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

  recipeTitle.textContent = recipe.title;
  recipeDescription.textContent = recipe.description;
  ingredientsList.innerHTML = "";
  instructionsList.innerHTML = "";

  if (Array.isArray(recipe.ingredients)) {
    recipe.ingredients.forEach(ingredient => {
      const li = document.createElement("li");
      li.textContent = ingredient;
      ingredientsList.appendChild(li);
    });
  } else {
    console.warn("⚠ Ingredients is not an array:", recipe.ingredients);
  }

  if (Array.isArray(recipe.instructions)) {
    recipe.instructions.forEach(instruction => {
      const li = document.createElement("li");
      li.textContent = instruction;
      instructionsList.appendChild(li);
    });
  } else {
    console.warn("⚠ Instructions is not an array:", recipe.instructions);
  }

  // Show Edit/Delete buttons for recipe only if logged-in user is the owner.
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

async function updateRecipeFromModal() {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }
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
    image_url: currentRecipe.image_url || "/images/default.png"
  };

  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}`, {
      method: "PUT",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(updateData)
    });
    if (response.ok) {
      alert("Recipe updated successfully!");
      fetchRecipeDetails(recipeId);
      const modalEl = document.getElementById("editRecipeModal");
      const modalInstance = bootstrap.Modal.getInstance(modalEl);
      modalInstance.hide();
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to update recipe.");
    }
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (response.ok) {
      alert("Recipe deleted successfully!");
      window.location.href = "recipes.html";
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete recipe.");
    }
  } catch (error) {
    console.error("Error deleting recipe:", error);
    alert(error.message);
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

// ---------- Comment Functions ----------
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ content })
    });
    if (response.ok) {
      fetchComments(recipeId);
      document.getElementById("comment").value = "";
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add comment.");
    }
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (response.ok) {
      let comments = await response.json();
      if (!Array.isArray(comments)) {
        console.warn("⚠ Comments is not an array:", comments);
        comments = [];
      }
      displayComments(comments);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch comments.");
    }
  } catch (error) {
    console.error("❌ Error fetching comments:", error);
    alert(error.message);
  }
}

function displayComments(comments) {
  const commentsList = document.getElementById("commentsList");
  commentsList.innerHTML = "";
  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);
  comments.forEach(comment => {
    let commentHtml = `
      <div class="mb-2">
        <strong>${comment.username}</strong> <small>${comment.createdAt}</small>
        <p>${comment.content}</p>`;
    // If the logged-in user is the author, add Edit/Delete buttons
    if (parseInt(comment.userId, 10) === loggedInUserId) {
      commentHtml += `
        <button class="btn btn-secondary btn-sm me-1" onclick="openEditCommentModal(${comment.id}, '${encodeURIComponent(comment.content)}')">Edit</button>
        <button class="btn btn-danger btn-sm" onclick="deleteComment(${comment.id})">Delete</button>`;
    }
    commentHtml += `</div>`;
    commentsList.innerHTML += commentHtml;
  });
}

function openEditCommentModal(commentId, encodedContent) {
  // Store the comment ID globally
  currentCommentId = commentId;
  // Decode the comment content in case of special characters
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ content: newContent })
    });
    if (response.ok) {
      alert("Comment updated successfully!");
      // Refresh comments; assume current recipe ID is in URL
      const recipeId = new URLSearchParams(window.location.search).get("id");
      fetchComments(recipeId);
      const modalEl = document.getElementById("editCommentModal");
      const modalInstance = bootstrap.Modal.getInstance(modalEl);
      modalInstance.hide();
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to update comment.");
    }
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (response.ok) {
      alert("Comment deleted successfully!");
      const recipeId = new URLSearchParams(window.location.search).get("id");
      fetchComments(recipeId);
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete comment.");
    }
  } catch (error) {
    console.error("Error deleting comment:", error);
    alert(error.message);
  }
}

// ---------- Rating Functions ----------
async function addRating(recipeId, rating) {
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ rating: parseInt(rating, 10) })
    });
    if (response.ok) {
      fetchRatings(recipeId);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to add rating.");
    }
  } catch (error) {
    console.error("❌ Error adding rating:", error);
    alert(error.message);
  }
}

async function fetchRatings(recipeId) {
  const token = localStorage.getItem("accessToken");
  try {
    const response = await fetch(`${API_URL}/recipes/${recipeId}/ratings`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (response.ok) {
      let ratings = await response.json();
      if (!Array.isArray(ratings)) {
        console.warn("⚠ Ratings is not an array:", ratings);
        ratings = [];
      }
      displayRatings(ratings);
    } else {
      const errorData = await response.json();
      throw new Error(errorData.message || "Failed to fetch ratings.");
    }
  } catch (error) {
    console.error("❌ Error fetching ratings:", error);
    alert(error.message);
  }
}

function displayRatings(ratings) {
  const ratingsList = document.getElementById("ratingsList");
  ratingsList.innerHTML = "";
  const loggedInUserId = parseInt(localStorage.getItem("userId"), 10);
  ratings.forEach(rating => {
    let ratingHtml = `
      <div class="mb-2">
        <strong>${rating.username}</strong> <small>${rating.createdAt}</small>
        <p>Rating: ${rating.rating}</p>`;
    if (parseInt(rating.userId, 10) === loggedInUserId) {
      ratingHtml += `
        <button class="btn btn-secondary btn-sm me-1" onclick="openEditRatingModal(${rating.id}, ${rating.rating})">Edit</button>
        <button class="btn btn-danger btn-sm" onclick="deleteRating(${rating.id})">Delete</button>`;
    }
    ratingHtml += `</div>`;
    ratingsList.innerHTML += ratingHtml;
  });
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ rating: newRating })
    });
    if (response.ok) {
      alert("Rating updated successfully!");
      const recipeId = new URLSearchParams(window.location.search).get("id");
      fetchRatings(recipeId);
      const modalEl = document.getElementById("editRatingModal");
      const modalInstance = bootstrap.Modal.getInstance(modalEl);
      modalInstance.hide();
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to update rating.");
    }
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (response.ok) {
      alert("Rating deleted successfully!");
      const recipeId = new URLSearchParams(window.location.search).get("id");
      fetchRatings(recipeId);
    } else {
      const errData = await response.json();
      throw new Error(errData.message || "Failed to delete rating.");
    }
  } catch (error) {
    console.error("Error deleting rating:", error);
    alert(error.message);
  }
}
