
const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    showCustomToast("Please log in first!", "danger");
    setTimeout(() => window.location.href = "login.html", 1500);
    return;
  }

  const form = document.getElementById("addRecipeForm");
  if (form) {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const submitButton = form.querySelector("button[type='submit']");
      submitButton.disabled = true;
      showLoadingSpinner();

      const title = document.getElementById("title").value.trim();
      const description = document.getElementById("description").value.trim();
      const ingredients = document.getElementById("ingredients").value.trim();
      const instructions = document.getElementById("instructions").value.trim();
      const imageFile = document.getElementById("imageUpload").files[0];

      if (!title || !description || !ingredients || !instructions) {
        showCustomToast("Please fill in all fields.", "danger");
        submitButton.disabled = false;
        hideLoadingSpinner();
        return;
      }

      let imageUrl = "/images/default.png";

      try {
        if (imageFile) {
          const imageFormData = new FormData();
          imageFormData.append("image", imageFile);

          const imageResponse = await fetch(`${API_URL}/upload`, {
            method: "POST",
            body: imageFormData,
          });

          const imageResult = await imageResponse.json();
          console.log("Image upload response:", imageResult);

          if (imageResponse.ok && imageResult.image_url) {
            imageUrl = imageResult.image_url;
          } else {
            console.error("Image upload failed:", imageResult);
            showCustomToast("⚠ Image upload failed. Using default image.", "warning");
          }
        }

        const recipeData = {
          title,
          description,
          ingredients,
          instructions,
          image_url: imageUrl,
        };

        console.log("📜 Sending recipe data:", recipeData);

        const recipeResponse = await fetch(`${API_URL}/recipes`, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(recipeData),
        });

        const recipeResult = await recipeResponse.json();
        console.log("✅ Recipe creation response:", recipeResult);

        if (recipeResponse.ok) {
          showCustomToast("Recipe added successfully!", "success");
          setTimeout(() => window.location.href = "recipes.html", 1500);
        } else {
          console.error("Recipe creation failed:", recipeResult);
          showCustomToast(`Failed to add recipe: ${recipeResult.message}`, "danger");
        }
      } catch (error) {
        console.error("Error in submission:", error);
        showCustomToast("Something went wrong!", "danger");
      } finally {
        hideLoadingSpinner();
        submitButton.disabled = false;
      }
    });
  }
});

function showLoadingSpinner() {
  document.getElementById("message").innerHTML = `
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  `;
}

function hideLoadingSpinner() {
  document.getElementById("message").innerHTML = "";
}

// Custom Toast Notification Function
function showCustomToast(message, type = "success") {
  const container = document.getElementById("toastContainer");
  if (!container) return;
  const toast = document.createElement("div");
  toast.classList.add("toast-custom", type);
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => toast.classList.add("show"), 100);
  setTimeout(() => {
    toast.classList.remove("show");
    setTimeout(() => container.removeChild(toast), 500);
  }, 3000);
}

function logout(event) {
  if (event) event.preventDefault();
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}
