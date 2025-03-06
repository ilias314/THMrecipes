document.addEventListener("DOMContentLoaded", () => {
    if (document.getElementById("recipes-list")) {
        loadRecipes();
    }

    const loginForm = document.getElementById("login-form");
    if (loginForm) {
        loginForm.addEventListener("submit", loginUser);
    }

    const registerForm = document.getElementById("register-form");
    if (registerForm) {
        registerForm.addEventListener("submit", registerUser);
    }
});

// 🔹 Rezepte laden
function loadRecipes() {
    fetch("http://localhost:8080/recipes")
        .then(response => response.json())
        .then(data => {
            const recipeList = document.getElementById("recipes-list");
            recipeList.innerHTML = "";
            data.forEach(recipe => {
                const recipeCard = `
                    <div class="col-md-4">
                        <div class="card">
                            <div class="card-body">
                                <h5 class="card-title">${recipe.name}</h5>
                                <p class="card-text">${recipe.description}</p>
                            </div>
                        </div>
                    </div>
                `;
                recipeList.innerHTML += recipeCard;
            });
        })
        .catch(error => console.error("Fehler beim Laden der Rezepte:", error));
}

// 🔹 Benutzer Login
function loginUser(event) {
    event.preventDefault();
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    fetch("http://localhost:8080/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                window.location.href = "index.html";
            }
        })
        .catch(error => console.error("Login fehlgeschlagen:", error));
}

// 🔹 Benutzer Registrierung
function registerUser(event) {
    event.preventDefault();
    const username = document.getElementById("username").value;
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    fetch("http://localhost:8080/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, email, password })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                window.location.href = "login.html";
            }
        })
        .catch(error => console.error("Registrierung fehlgeschlagen:", error));
}
