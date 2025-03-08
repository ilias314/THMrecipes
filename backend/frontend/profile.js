// profile.js

// Function to fetch user profile with token refresh support
async function fetchProfile() {
  let token = localStorage.getItem("accessToken");

  // If token is not found, try to refresh it
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
    // Fetch user profile from the backend
    const profileResponse = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    // If unauthorized, try refreshing the token and retry
    if (profileResponse.status === 401) {
      token = await refreshToken();
      if (token) {
        localStorage.setItem("accessToken", token);
        return fetchProfile(); // Retry fetching profile with new token
      } else {
        alert("Session expired. Please log in again.");
        window.location.href = "login.html";
        return;
      }
    }

    if (profileResponse.ok) {
      const user = await profileResponse.json();
      displayProfile(user);
    } else {
      const errorData = await profileResponse.json();
      throw new Error(errorData.message || "Failed to fetch profile.");
    }

    // Fetch user recipes
    const recipesResponse = await fetch(`http://localhost:8080/users/${userId}/recipes`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (recipesResponse.ok) {
      const recipes = await recipesResponse.json();
      displayUserRecipes(recipes);
    } else {
      throw new Error("Failed to fetch user recipes.");
    }

    // Fetch user comments
    const commentsResponse = await fetch(`http://localhost:8080/users/${userId}/comments`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (commentsResponse.ok) {
      const comments = await commentsResponse.json();
      displayUserComments(comments);
    } else {
      throw new Error("Failed to fetch user comments.");
    }

    // Fetch user ratings
    const ratingsResponse = await fetch(`http://localhost:8080/users/${userId}/ratings`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });
    if (ratingsResponse.ok) {
      const ratings = await ratingsResponse.json();
      displayUserRatings(ratings);
    } else {
      throw new Error("Failed to fetch user ratings.");
    }
  } catch (error) {
    console.error("Error:", error);
    alert(error.message);
  }
}

// Function to display user profile information
function displayProfile(user) {
  const profileInfo = document.getElementById("profileInfo");
  profileInfo.innerHTML = `
    <p>Username: ${user.username}</p>
    <p>Email: ${user.email}</p>
  `;
}

// Function to display user recipes
function displayUserRecipes(recipes) {
  const userRecipes = document.getElementById("userRecipes");
  userRecipes.innerHTML = "";
  recipes.forEach(recipe => {
    const recipeDiv = document.createElement("div");
    recipeDiv.className = "recipe";
    recipeDiv.innerHTML = `
      <h3>${recipe.title}</h3>
      <p>${recipe.description}</p>
      <a href="recipe-detail.html?id=${recipe.id}">View Recipe</a>
    `;
    userRecipes.appendChild(recipeDiv);
  });
}

// Function to display user comments
function displayUserComments(comments) {
  const userComments = document.getElementById("userComments");
  userComments.innerHTML = "";
  comments.forEach(comment => {
    const commentDiv = document.createElement("div");
    commentDiv.className = "comment";
    commentDiv.innerHTML = `
      <h3>${comment.recipeTitle}</h3>
      <p>${comment.content}</p>
      <p>${comment.createdAt}</p>
    `;
    userComments.appendChild(commentDiv);
  });
}

// Function to display user ratings
function displayUserRatings(ratings) {
  const userRatings = document.getElementById("userRatings");
  userRatings.innerHTML = "";
  ratings.forEach(rating => {
    const ratingDiv = document.createElement("div");
    ratingDiv.className = "rating";
    ratingDiv.innerHTML = `
      <h3>${rating.recipeTitle}</h3>
      <p>Rating: ${rating.rating}</p>
      <p>${rating.createdAt}</p>
    `;
    userRatings.appendChild(ratingDiv);
  });
}

// Function to open the edit profile modal
function openEditProfileModal() {
  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("accessToken");
  fetch(`http://localhost:8080/users/${userId}`, {
    method: "GET",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    }
  })
    .then(response => response.json())
    .then(user => {
      document.getElementById("username").value = user.username;
      document.getElementById("email").value = user.email;
      const editProfileModal = new bootstrap.Modal(document.getElementById('editProfileModal'));
      editProfileModal.show();
    })
    .catch(error => {
      console.error("Error fetching user data:", error);
      alert("Failed to fetch user data.");
    });
}

// Function to update the user profile
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
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(updateData)
    });

    // If unauthorized, attempt to refresh token and retry
    if (response.status === 401) {
      token = await refreshToken();
      if (token) {
        localStorage.setItem("accessToken", token);
        response = await fetch(`http://localhost:8080/users/${userId}`, {
          method: "PUT",
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify(updateData)
        });
      } else {
        alert("Your session has expired. Please log in again.");
        localStorage.removeItem("accessToken");
        localStorage.removeItem("userId");
        window.location.href = "login.html";
        return;
      }
    }

    // Use clone() to safely attempt JSON parsing without consuming the original stream
    let responseData;
    try {
      responseData = await response.clone().json();
    } catch (e) {
      responseData = await response.text();
    }

    if (response.ok) {
      alert("Profile updated successfully!");
      const editProfileModal = bootstrap.Modal.getInstance(document.getElementById('editProfileModal'));
      editProfileModal.hide();
      fetchProfile();
    } else {
      throw new Error((responseData && responseData.message) || responseData || "Failed to update profile.");
    }
  } catch (error) {
    console.error("Error updating profile:", error);
    alert(error.message);
  }
}

// Function to delete the user profile
async function deleteProfile() {
  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("accessToken");

  const confirmDelete = confirm("Are you sure you want to delete your profile? This action cannot be undone.");
  if (!confirmDelete) return;

  try {
    const response = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "DELETE",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    if (response.status === 401) {
      alert("Your session has expired. Please log in again.");
      localStorage.removeItem("accessToken");
      localStorage.removeItem("userId");
      window.location.href = "login.html";
      return;
    }

    // Use clone() to safely parse the response body
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
      throw new Error((responseData && responseData.message) || responseData || "Failed to delete profile.");
    }
  } catch (error) {
    console.error("Error deleting profile:", error);
    alert(error.message);
  }
}

// Fetch profile, recipes, comments, and ratings when the page loads
document.addEventListener("DOMContentLoaded", fetchProfile);
