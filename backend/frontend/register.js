async function register() {
  const username = document.getElementById("username").value;
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!username || !email || !password) {
    document.getElementById("message").textContent = "Bitte alle Felder ausfüllen!";
    return;
  }

  try {
    const response = await fetch("http://localhost:8080/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password })
    });

    const result = await response.text();
    document.getElementById("message").textContent = result;

    if (response.ok) {
      window.location.href = "login.html";
    }
  } catch (error) {
    console.error("Registrierung fehlgeschlagen:", error);
  }
}
