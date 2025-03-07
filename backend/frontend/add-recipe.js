const API_URL = "http://localhost:8080";

document.addEventListener("DOMContentLoaded", () => {
  const token = localStorage.getItem("token");

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
      submitButton.disabled = true; // Disable submit button
      showLoadingSpinner(); // Show spinner

      const title = document.getElementById("title").value;
      const description = document.getElementById("description").value;
      const ingredients = document.getElementById("ingredients").value.split(",").map(item => item.trim());
      const instructions = document.getElementById("instructions").value.split("\n").map(item => item.trim());
      const imageFile = document.getElementById("imageUpload").files[0];

      // Validate file type
      if (!imageFile.type.startsWith("image/")) {
        showToast("❌ Please upload a valid image file!", "danger");
        hideLoadingSpinner();
        submitButton.disabled = false;
        return;
      }

      if (!title || !description || !ingredients.length || !instructions.length || !imageFile) {
        showToast("❌ Please fill in all fields!", "danger");
        hideLoadingSpinner();
        submitButton.disabled = false;
        return;
      }

      try {
        // Step 1: Upload the image
        const imageFormData = new FormData();
        imageFormData.append("image", imageFile);

        const imageResponse = await fetch(`${API_URL}/upload`, {
          method: "POST",
          body: imageFormData,
        });

        if (!imageResponse.ok) {
          throw new Error("Failed to upload image.");
        }

        const imageResult = await imageResponse.json();
        const imageUrl = imageResult.image_url;

        // Step 2: Add the recipe
        const recipeData = {
          title,
          description,
          ingredients,
          instructions,
          imageUrl,
        };

        const recipeResponse = await fetch(`${API_URL}/recipes`, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(recipeData),
        });

        if (recipeResponse.ok) {
          showToast("✅ Recipe added successfully!", "success");
          setTimeout(() => {
            window.location.href = "recipes.html"; // Redirect to recipes page
          }, 2000);
        } else {
          const errorData = await recipeResponse.json();
          throw new Error(errorData.message || "Failed to add recipe.");
        }
      } catch (error) {
        console.error("❌ Error adding recipe:", error);
        console.error("Error details:", error.stack); // Log the full error stack
        showToast(`❌ ${error.message}`, "danger");
      } finally {
        hideLoadingSpinner(); // Hide spinner
        submitButton.disabled = false; // Re-enable submit button
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

function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  const toastBody = toast.querySelector(".toast-body");

  toastBody.textContent = message;
  toast.classList.remove("bg-success", "bg-danger");
  toast.classList.add(`bg-${type}`);

  const toastInstance = new bootstrap.Toast(toast);
  toastInstance.show();
}
