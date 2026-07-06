function initCountdownTimer(root) {
  const instanceId = root.dataset.instanceId;
  const eventDate = root.dataset.eventDate;
  const timer = document.getElementById(`countdown-timer-${instanceId}`);
  if (!timer || !eventDate) return;
  const target = new Date(`${eventDate}T00:00:00`);
  const render = () => {
    const diff = Math.max(0, target.getTime() - Date.now());
    const days = Math.floor(diff / 86400000);
    const hours = Math.floor((diff % 86400000) / 3600000);
    const minutes = Math.floor((diff % 3600000) / 60000);
    timer.textContent = `${days} дн. ${hours} ч. ${minutes} мин.`;
  };
  render();
  setInterval(render, 60000);
}

function completeCountdownTask(buttonOrInstanceId, maybeGuestId, maybeTaskId) {
  const button = typeof buttonOrInstanceId === "object" ? buttonOrInstanceId : null;
  const instanceId = button ? button.dataset.instanceId : buttonOrInstanceId;
  const guestId = button ? button.dataset.guestId : maybeGuestId;
  const taskId = button ? button.dataset.taskId : maybeTaskId;
  const card = document.getElementById(`countdown-task-${instanceId}-${taskId}`);
  const output = document.getElementById(`countdown-result-${instanceId}`);
  if (!card) return;
  const type = card.dataset.taskType || "text";
  const input = document.getElementById(`countdown-answer-${instanceId}-${taskId}`);
  const payload = {
    guestId,
    type: "complete_task",
    taskId
  };
  if (type === "checkbox") {
    payload.completed = Boolean(input?.checked);
  } else {
    payload.answer = input?.value || "";
  }
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  })
    .then((response) => response.json())
    .then((result) => {
      if (output) output.textContent = result.message;
      if (result.success) {
        card.classList.add("completed");
        card.querySelector("button")?.setAttribute("disabled", "disabled");
      }
    })
    .catch(() => {
      if (output) output.textContent = "Не удалось выполнить задание.";
    });
}

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".countdown-challenge[data-instance-id]").forEach(initCountdownTimer);
});
