document.getElementById("addRecipeForm").addEventListener("submit", async function (event) {
  event.preventDefault();

  const title = document.getElementById("title").value;
  const description = document.getElementById("description").value;
  const ingredients = document.getElementById("ingredients").value.split(",").map(item => item.trim());
  const instructions = document.getElementById("instructions").value.split(",").map(item => item.trim());
  const imageFile = document.getElementById("image").files[0];
  const message = document.getElementById("message");

  if (!title || !ingredients.length || !instructions.length || !imageFile) {
    message.textContent = "Please fill in all fields!";
    return;
  }

  const token = localStorage.getItem("token");

  if (!token) {
    alert("Please log in first!");
    window.location.href = "login.html";
    return;
  }

  try {
    // Step 1: Upload the image
    const imageFormData = new FormData();
    imageFormData.append("image", imageFile);

    const imageResponse = await fetch("http://localhost:8080/upload", {
      method: "POST",
      body: imageFormData,
    });

    if (!imageResponse.ok) {
      throw new Error("Failed to upload image.");
    }

    const imageResult = await imageResponse.json();
    const imageUrl = imageResult.image_url;

    // Step 2: Add the recipe with the image URL
    const recipeResponse = await fetch("http://localhost:8080/recipes", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        title,
        description,
        ingredients,
        instructions,
        imageUrl,
      }),
    });

    if (recipeResponse.ok) {
      message.textContent = "✅ Recipe added successfully!";
      window.location.href = "recipes.html"; // Redirect to recipes page
    } else {
      const errorData = await recipeResponse.json();
      throw new Error(errorData.message || "Failed to add recipe.");
    }
  } catch (error) {
    console.error("❌ Error:", error);
    message.textContent = error.message;
  }
});
