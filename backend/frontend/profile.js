// profile.js

async function fetchProfile() {
  let token = localStorage.getItem("accessToken");
  if (!token) {
    token = await refreshToken();
    if (!token) {
      alert("Please log in first!");
      window.location.href = "login.html";
      return;
    }
  }

  const userId = localStorage.getItem("userId");
  if (!userId) {
    alert("User ID is missing!");
    return;
  }

  try {
    // 1) Fetch user profile
    const profileResponse = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });
    if (profileResponse.status === 401) {
      token = await refreshToken();
      if (token) {
        localStorage.setItem("accessToken", token);
        return fetchProfile();
      } else {
        alert("Session expired. Please log in again.");
        window.location.href = "login.html";
        return;
      }
    }
    if (!profileResponse.ok) {
      const errorData = await profileResponse.json();
      throw new Error(errorData.message || "Failed to fetch profile.");
    }
    const user = await profileResponse.json();
    displayProfile(user);

    // 2) Fetch user recipes
    const recipesResponse = await fetch(
      `http://localhost:8080/users/${userId}/recipes`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      }
    );
    if (!recipesResponse.ok) {
      throw new Error("Failed to fetch user recipes.");
    }
    const userRecipes = await recipesResponse.json();
    displayUserRecipes(userRecipes);

    // 3) Fetch user comments
    const commentsResponse = await fetch(
      `http://localhost:8080/users/${userId}/comments`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      }
    );
    if (!commentsResponse.ok) {
      throw new Error("Failed to fetch user comments.");
    }
    const comments = await commentsResponse.json();
    displayUserComments(comments);

    // 4) Fetch user ratings
    const ratingsResponse = await fetch(
      `http://localhost:8080/users/${userId}/ratings`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      }
    );
    if (!ratingsResponse.ok) {
      throw new Error("Failed to fetch user ratings.");
    }
    const ratings = await ratingsResponse.json();
    displayUserRatings(ratings);
  } catch (error) {
    console.error("Error:", error);
    alert(error.message);
  }
}

// Display user profile
function displayProfile(user) {
  const profileInfo = document.getElementById("profileInfo");
  profileInfo.innerHTML = `
    <p><strong>Username:</strong> ${user.username}</p>
    <p><strong>Email:</strong> ${user.email}</p>
  `;
}

// Display user recipes in the same layout as recipes page
async function displayUserRecipes(recipes) {
  const userRecipesContainer = document.getElementById("userRecipes");
  if (!userRecipesContainer) return;
  userRecipesContainer.innerHTML = "";

  // We will group 3 cards per row
  for (let i = 0; i < recipes.length; i += 3) {
    const row = document.createElement("div");
    row.className = "row mb-4";

    for (let j = i; j < i + 3 && j < recipes.length; j++) {
      const recipe = recipes[j];

      // Handle image
      let imageUrl = recipe.image_url || "default-image.jpg";
      if (!imageUrl.startsWith("http") && !imageUrl.startsWith("/")) {
        imageUrl = `/images/${imageUrl}`;
      }

      // We'll allow editing or deleting from recipe detail or from a prompt
      // (Optional) If you want direct editing here, you can replicate the code from recipes.js
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
              <!-- Container for average rating -->
              <div id="avgRating-user-${recipe.id}" class="mb-2"></div>
              <a href="recipe-detail.html?id=${recipe.id}" class="btn btn-primary mb-2">
                View Recipe
              </a>
            </div>
          </div>
        </div>
      `;
      row.innerHTML += recipeCard;
    }
    userRecipesContainer.appendChild(row);
  }

  // After building the cards, fetch & display average rating for each recipe
  for (const recipe of recipes) {
    await fetchAndDisplayAverageRatingForProfile(recipe.id);
  }
}

/**
 * Similar function to fetch and display average rating,
 * but IDs are slightly different here ("avgRating-user-...")
 */
async function fetchAndDisplayAverageRatingForProfile(recipeId) {
  try {
    const token = localStorage.getItem("accessToken");
    const response = await fetch(
      `http://localhost:8080/recipes/${recipeId}/ratings`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      }
    );
    if (!response.ok) throw new Error("Failed to fetch ratings");

    const ratings = await response.json();
    const ratingContainer = document.getElementById(`avgRating-user-${recipeId}`);
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

/**
 * Convert numeric rating to star icons (same as recipes.js).
 */
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

// Display user comments
function displayUserComments(comments) {
  const userComments = document.getElementById("userComments");
  userComments.innerHTML = "";
  if (!comments || !Array.isArray(comments)) return;

  comments.forEach((comment) => {
    const commentDiv = document.createElement("div");
    commentDiv.className = "comment card p-2 mb-2";
    commentDiv.innerHTML = `
      <strong>${comment.recipeTitle}</strong>
      <p>${comment.content}</p>
      <small>${comment.createdAt}</small>
    `;
    userComments.appendChild(commentDiv);
  });
}

// Display user ratings
function displayUserRatings(ratings) {
  const userRatings = document.getElementById("userRatings");
  userRatings.innerHTML = "";
  if (!ratings || !Array.isArray(ratings)) return;

  ratings.forEach((rating) => {
    const ratingDiv = document.createElement("div");
    ratingDiv.className = "rating card p-2 mb-2";
    ratingDiv.innerHTML = `
      <strong>${rating.recipeTitle}</strong>
      <p>Rating: ${rating.rating}</p>
      <small>${rating.createdAt}</small>
    `;
    userRatings.appendChild(ratingDiv);
  });
}

// Open edit profile modal
function openEditProfileModal() {
  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("accessToken");

  fetch(`http://localhost:8080/users/${userId}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  })
    .then((response) => response.json())
    .then((user) => {
      document.getElementById("username").value = user.username;
      document.getElementById("email").value = user.email;
      const editProfileModal = new bootstrap.Modal(
        document.getElementById("editProfileModal")
      );
      editProfileModal.show();
    })
    .catch((error) => {
      console.error("Error fetching user data:", error);
      alert("Failed to fetch user data.");
    });
}

// Update the user profile
async function updateProfile() {
  const userId = localStorage.getItem("userId");
  let token = localStorage.getItem("accessToken");
  const username = document.getElementById("username").value;
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  const updateData = { username, email };
  if (password) {
    updateData.password = password;
  }

  try {
    let response = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(updateData),
    });

    if (response.status === 401) {
      token = await refreshToken();
      if (token) {
        localStorage.setItem("accessToken", token);
        response = await fetch(`http://localhost:8080/users/${userId}`, {
          method: "PUT",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(updateData),
        });
      } else {
        alert("Your session has expired. Please log in again.");
        localStorage.removeItem("accessToken");
        localStorage.removeItem("userId");
        window.location.href = "login.html";
        return;
      }
    }

    let responseData;
    try {
      responseData = await response.clone().json();
    } catch (e) {
      responseData = await response.text();
    }

    if (response.ok) {
      alert("Profile updated successfully!");
      const editProfileModal = bootstrap.Modal.getInstance(
        document.getElementById("editProfileModal")
      );
      editProfileModal.hide();
      fetchProfile();
    } else {
      throw new Error(
        (responseData && responseData.message) || responseData || "Failed to update profile."
      );
    }
  } catch (error) {
    console.error("Error updating profile:", error);
    alert(error.message);
  }
}

// Delete the user profile
async function deleteProfile() {
  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("accessToken");

  const confirmDelete = confirm(
    "Are you sure you want to delete your profile? This action cannot be undone."
  );
  if (!confirmDelete) return;

  try {
    const response = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
    });

    if (response.status === 401) {
      alert("Your session has expired. Please log in again.");
      localStorage.removeItem("accessToken");
      localStorage.removeItem("userId");
      window.location.href = "login.html";
      return;
    }

    let responseData;
    try {
      responseData = await response.clone().json();
    } catch (e) {
      responseData = await response.text();
    }

    if (response.ok) {
      alert("Profile deleted successfully!");
      localStorage.removeItem("accessToken");
      localStorage.removeItem("userId");
      window.location.href = "login.html";
    } else {
      throw new Error(
        (responseData && responseData.message) || responseData || "Failed to delete profile."
      );
    }
  } catch (error) {
    console.error("Error deleting profile:", error);
    alert(error.message);
  }
}

// On page load
document.addEventListener("DOMContentLoaded", fetchProfile);
