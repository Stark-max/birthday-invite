function playTruthOrDare(instanceId, guestId, choice) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, choice })
  })
    .then((r) => r.json())
    .then((result) => {
      const card = document.getElementById(`tod-result-${instanceId}`);
      card.textContent = result.message;
      card.classList.add("flip");
      setTimeout(() => card.classList.remove("flip"), 350);
    });
}

function addTruthOrDarePrompt(event, instanceId, guestId) {
  event.preventDefault();
  const choice = document.getElementById(`tod-add-choice-${instanceId}`)?.value || "truth";
  const input = document.getElementById(`tod-add-prompt-${instanceId}`);
  const output = document.getElementById(`tod-add-result-${instanceId}`);
  const prompt = input?.value.trim() || "";
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "add", choice, prompt })
  })
    .then((r) => r.json())
    .then((result) => {
      if (output) output.textContent = result.message;
      if (result.success && input) input.value = "";
    });
}

function completeChallenge(instanceId, guestId, challengeIndex) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "complete", challengeIndex })
  })
    .then((r) => r.json())
    .then((result) => {
      const el = document.getElementById(`challenge-result-${instanceId}`);
      el.textContent = result.message;
    });
}

function voteChallenge(instanceId, guestId, targetResultId) {
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId, action: "vote", targetResultId })
  })
    .then((r) => r.json())
    .then((result) => {
      const el = document.getElementById(`challenge-result-${instanceId}`);
      el.textContent = result.message;
    });
}
