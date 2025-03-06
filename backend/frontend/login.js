async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!email || !password) {
    document.getElementById("message").textContent = "Bitte alle Felder ausfüllen!";
    return;
  }

  try {
    const response = await fetch("http://localhost:8080/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });

    const result = await response.json();

    if (response.ok) {
      if (!result.token || !result.userId) {
        throw new Error("Antwort vom Server ist ungültig! `userId` oder `token` fehlt.");
      }

      // Speichere Token & User-Daten
      localStorage.setItem("token", result.token);
      localStorage.setItem("userId", result.userId);

      console.log(`🔑 Erfolgreich eingeloggt! userId: ${result.userId}`);

      // Weiterleitung zum Dashboard
      window.location.href = "home.html";
    } else {
      throw new Error(result.message || "Login fehlgeschlagen!");
    }
  } catch (error) {
    console.error("❌ Fehler beim Login:", error);
    document.getElementById("message").textContent = error.message;
  }
}

// ✅ Funktion zum Abrufen geschützter Daten nach dem Login
async function getProtectedData() {
  const token = localStorage.getItem("token");

  if (!token) {
    document.getElementById("message").textContent = "Bitte zuerst einloggen!";
    return;
  }

  try {
    const response = await fetch("http://localhost:8080/protected-route", {
      method: "GET",
      headers: { "Authorization": `Bearer ${token}` }
    });

    const data = await response.json();

    if (response.ok) {
      console.log("✅ Geschützte Daten:", data);
    } else {
      throw new Error(data.message || "Fehler beim Abrufen der Daten.");
    }
  } catch (error) {
    console.error("❌ Fehler:", error);
    document.getElementById("message").textContent = error.message;
  }
}

// ✅ Logout-Funktion
function logout() {
  localStorage.removeItem("token");
  localStorage.removeItem("userId");
  window.location.href = "login.html";
}

// Optional: Event Listener für die Enter-Taste
document.addEventListener("keypress", function (event) {
  if (event.key === "Enter") {
    login();
  }
});
