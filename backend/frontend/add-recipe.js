// add-recipe.js
const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  // Check for a valid token in localStorage
  const token = localStorage.getItem("accessToken");
  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
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
      // Split by comma, trim each item
      const ingredients = document.getElementById("ingredients").value
        .split(",")
        .map(item => item.trim());
      // Split by newline, trim each line
      const instructions = document.getElementById("instructions").value
        .split("\n")
        .map(item => item.trim());
      const imageFile = document.getElementById("imageUpload").files[0];

      if (!imageFile) {
        console.error("❌ No image selected!");
        showToast("❌ Please select an image.", "danger");
        submitButton.disabled = false;
        hideLoadingSpinner();
        return;
      }

      try {
        // 1) Upload the Image
        const imageFormData = new FormData();
        imageFormData.append("image", imageFile);

        const imageResponse = await fetch(`${API_URL}/upload`, {
          method: "POST",
          body: imageFormData,
        });

        const imageResult = await imageResponse.json();
        console.log("📤 Image upload response:", imageResult);

        if (!imageResponse.ok || !imageResult.image_url) {
          console.error("❌ Image upload failed:", imageResult);
          showToast(`❌ Image upload failed: ${JSON.stringify(imageResult)}`, "danger");
          submitButton.disabled = false;
          hideLoadingSpinner();
          return;
        }

        const imageUrl = imageResult.image_url;
        console.log("🖼️ Image URL received:", imageUrl);

        // 2) Send Recipe Data to Backend
        const recipeData = {
          title,
          description,
          ingredients,
          instructions,
          image_url: imageUrl, // Use the URL from the upload response
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
          showToast("✅ Recipe added successfully!", "success");
          setTimeout(() => {
            window.location.href = "recipes.html"; // Redirect to recipes page
          }, 1500);
        } else {
          console.error("❌ Recipe creation failed:", recipeResult);
          showToast(`❌ Failed to add recipe: ${recipeResult.message}`, "danger");
        }
      } catch (error) {
        console.error("❌ Error in submission:", error);
        showToast("❌ Something went wrong!", "danger");
      } finally {
        hideLoadingSpinner();
        submitButton.disabled = false;
      }
    });
  }
});

// Show a loading spinner
function showLoadingSpinner() {
  document.getElementById("message").innerHTML = `
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  `;
}

// Hide the loading spinner
function hideLoadingSpinner() {
  document.getElementById("message").innerHTML = "";
}

// Show a toast notification
function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  const toastBody = toast.querySelector(".toast-body");

  toastBody.textContent = message;
  toast.classList.remove("bg-success", "bg-danger");
  toast.classList.add(`bg-${type}`);

  const toastInstance = new bootstrap.Toast(toast);
  toastInstance.show();
}
