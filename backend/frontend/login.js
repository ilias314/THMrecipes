const API_URL = "http://localhost:8080";

async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!email || !password) {
    document.getElementById("message").textContent = "Bitte alle Felder ausfüllen!";
    return;
  }

  try {
    const response = await fetch(`${API_URL}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });

    const result = await response.json();

    if (response.ok) {
      if (!result.token || !result.userId) {
        throw new Error("Antwort vom Server ist ungültig! `userId` oder `token` fehlt.");
      }

      localStorage.setItem("token", result.token);
      localStorage.setItem("userId", result.userId);

      console.log(`🔑 Erfolgreich eingeloggt! userId: ${result.userId}`);
      window.location.href = "index.html";
    } else {
      throw new Error(result.message || "Login fehlgeschlagen!");
    }
  } catch (error) {
    console.error("❌ Fehler beim Login:", error);
    document.getElementById("message").textContent = error.message;
  }
}
