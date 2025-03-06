document.addEventListener("DOMContentLoaded", async () => {
    const userId = localStorage.getItem("userId");
    const token = localStorage.getItem("token");

    if (!userId || !token) {
        alert("❌ Nicht eingeloggt!");
        window.location.href = "login.html";
        return;
    }

    try {
        const response = await fetch(`http://localhost:8080/users/${userId}`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            }
        });

        if (!response.ok) throw new Error("Fehler beim Laden der Benutzerinformationen");

        const userData = await response.json();
        document.getElementById("username").value = userData.username;
        document.getElementById("email").value = userData.email;

    } catch (error) {
        console.error("❌ Fehler:", error);
    }
});

// ✅ Update-Funktion für den "Speichern"-Button
async function updateProfile() {
  const userId = localStorage.getItem("userId");
  const token = localStorage.getItem("token");

  const username = document.getElementById("username").value;
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  if (!username || !email || !password) {
    alert("❌ Alle Felder müssen ausgefüllt sein!");
    return;
  }

  try {
    const response = await fetch(`http://localhost:8080/users/${userId}`, {
      method: "PUT",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ username, email, password })
    });

    if (!response.ok) throw new Error("❌ Fehler beim Aktualisieren des Profils!");

    alert("✅ Profil erfolgreich aktualisiert!");
  } catch (error) {
    console.error("❌ Fehler beim Aktualisieren:", error);
  }
}

